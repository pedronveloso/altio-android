/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.runtime

import app.altio.service.domain.job.JobRepository
import app.altio.service.domain.job.JobStatus
import app.altio.service.domain.runtime.InferenceChunk
import app.altio.service.domain.runtime.InferenceRequest
import app.altio.service.domain.runtime.Message
import app.altio.service.domain.runtime.Part
import app.altio.service.domain.runtime.Role
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

sealed class SchedulerWork {
  abstract val jobId: String
  abstract val sessionId: String

  data class Generate(
      override val jobId: String,
      override val sessionId: String,
      val request: InferenceRequest,
  ) : SchedulerWork()

  class Transcribe(
      override val jobId: String,
      override val sessionId: String,
      val audioBytes: ByteArray,
      val mimeType: String,
  ) : SchedulerWork()
}

/**
 * Serializes inference and transcription jobs through a single-consumer [Channel]. Only one job
 * runs at a time to avoid GPU/NPU contention.
 *
 * Each job's token stream is exposed as a [SharedFlow] with unlimited replay so SSE clients can
 * subscribe at any point and receive all tokens emitted so far. Transcription jobs emit a single
 * [InferenceChunk.Done] terminal event when the transcript is ready.
 */
open class InferenceScheduler(
    private val scope: CoroutineScope,
    private val sessionManager: RuntimeSessionManager,
    private val jobRepository: JobRepository,
) {
  private val queue = Channel<SchedulerWork>(QUEUE_CAPACITY)
  private val jobStreams = ConcurrentHashMap<String, MutableSharedFlow<InferenceChunk>>()

  /** Coroutine jobs for currently-running work, keyed by job ID. */
  private val activeJobs = ConcurrentHashMap<String, Job>()

  init {
    scope.launch { for (work in queue) processWork(work) }
  }

  fun enqueue(work: SchedulerWork): Boolean {
    val success = queue.trySend(work).isSuccess
    if (success) {
      Timber.d("Enqueued job %s (session %s)", work.jobId, work.sessionId)
      jobStreams.getOrPut(work.jobId) { newJobStream() }
    }
    return success
  }

  /**
   * Cancels a running or queued job. If the job is actively running its coroutine is cancelled,
   * which triggers [CancellationException] in the inference loop and updates the DB status. If the
   * job is still in the queue its DB row is updated directly.
   */
  fun cancelJob(jobId: String) {
    val running = activeJobs[jobId]
    if (running != null) {
      running.cancel()
    } else {
      scope.launch {
        val job = jobRepository.getJob(jobId).first()
        if (job?.status == JobStatus.QUEUED) {
          jobRepository.cancel(jobId, Instant.now())
          jobStreams[jobId]?.let { stream ->
            emitTerminalCancellationIfNeeded(stream)
            scheduleStreamCleanup(jobId)
          }
        }
      }
    }
  }

  /** Returns the replay-buffered stream for the given job, or null if no such job exists. */
  open fun streamJob(jobId: String): SharedFlow<InferenceChunk>? = jobStreams[jobId]

  private suspend fun processWork(work: SchedulerWork) {
    val stream = jobStreams.getOrPut(work.jobId) { newJobStream() }
    val job = jobRepository.getJob(work.jobId).first()
    if (job == null) {
      stream.emit(InferenceChunk.Error(RuntimeException("JOB_NOT_FOUND")))
      scheduleStreamCleanup(work.jobId)
      return
    }
    if (job.status == JobStatus.CANCELLED) {
      Timber.d("Skipping cancelled queued job %s", work.jobId)
      emitTerminalCancellationIfNeeded(stream)
      scheduleStreamCleanup(work.jobId)
      return
    }

    jobRepository.updateStarted(work.jobId, Instant.now())

    val session = sessionManager.getSession(work.sessionId)
    if (session == null) {
      Timber.w("Session %s not found for job %s", work.sessionId, work.jobId)
      stream.emit(InferenceChunk.Error(RuntimeException("SESSION_NOT_FOUND")))
      jobRepository.fail(work.jobId, "SESSION_NOT_FOUND", Instant.now())
      scheduleStreamCleanup(work.jobId)
      return
    }

    val activeJob =
        scope.launch {
          try {
            when (work) {
              is SchedulerWork.Generate -> processGenerate(work, stream, session)
              is SchedulerWork.Transcribe -> processTranscription(work, stream, session)
            }
          } catch (e: CancellationException) {
            Timber.d("Job %s cancelled (session %s)", work.jobId, work.sessionId)
            stream.emit(InferenceChunk.Error(CancellationException("CANCELLED")))
            jobRepository.cancel(work.jobId, Instant.now())
            throw e
          } catch (e: Exception) {
            Timber.e(e, "Inference failed for job %s", work.jobId)
            val alreadyHasError = stream.replayCache.any { it is InferenceChunk.Error }
            if (!alreadyHasError) stream.emit(InferenceChunk.Error(e))
            jobRepository.fail(
                work.jobId,
                e.message?.take(128) ?: "INFERENCE_ERROR",
                Instant.now(),
            )
          } finally {
            scheduleStreamCleanup(work.jobId)
          }
        }

    activeJobs[work.jobId] = activeJob
    try {
      activeJob.join()
    } finally {
      activeJobs.remove(work.jobId)
    }
  }

  private suspend fun processGenerate(
      work: SchedulerWork.Generate,
      stream: MutableSharedFlow<InferenceChunk>,
      session: app.altio.service.domain.runtime.RuntimeSession,
  ) {
    val output = StringBuilder()
    session.generateStream(work.request).collect { chunk ->
      stream.emit(chunk)
      when (chunk) {
        is InferenceChunk.Token -> output.append(chunk.text)
        is InferenceChunk.Done -> {}
        is InferenceChunk.Error -> throw chunk.cause
      }
    }
    jobRepository.complete(work.jobId, output.toString(), Instant.now())
  }

  private suspend fun processTranscription(
      work: SchedulerWork.Transcribe,
      stream: MutableSharedFlow<InferenceChunk>,
      session: app.altio.service.domain.runtime.RuntimeSession,
  ) {
    val request =
        InferenceRequest(
            messages =
                listOf(
                    Message(
                        role = Role.SYSTEM,
                        parts =
                            listOf(
                                Part.Text(
                                    "You are a transcription assistant. Transcribe the audio exactly as spoken. Output only the transcript text, with no commentary."
                                )
                            ),
                    ),
                    Message(
                        role = Role.USER,
                        parts = listOf(Part.Audio(work.audioBytes)),
                    ),
                )
        )
    val transcript = StringBuilder()
    session.generateStream(request).collect { chunk ->
      stream.emit(chunk)
      when (chunk) {
        is InferenceChunk.Token -> transcript.append(chunk.text)
        is InferenceChunk.Done -> {}
        is InferenceChunk.Error -> throw chunk.cause
      }
    }
    jobRepository.complete(work.jobId, transcript.toString(), Instant.now())
  }

  private fun scheduleStreamCleanup(jobId: String) {
    scope.launch {
      delay(STREAM_TTL_MS)
      jobStreams.remove(jobId)
    }
  }

  private suspend fun emitTerminalCancellationIfNeeded(stream: MutableSharedFlow<InferenceChunk>) {
    val hasTerminalChunk =
        stream.replayCache.any { chunk ->
          chunk is InferenceChunk.Done || chunk is InferenceChunk.Error
        }
    if (!hasTerminalChunk) {
      stream.emit(InferenceChunk.Error(CancellationException("CANCELLED")))
    }
  }

  companion object {
    private const val STREAM_TTL_MS = 60_000L
    private const val STREAM_REPLAY = 64
    private const val STREAM_EXTRA_BUFFER_CAPACITY = 64
    private const val QUEUE_CAPACITY = 32

    private fun newJobStream(): MutableSharedFlow<InferenceChunk> =
        MutableSharedFlow(
            replay = STREAM_REPLAY,
            extraBufferCapacity = STREAM_EXTRA_BUFFER_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
  }
}
