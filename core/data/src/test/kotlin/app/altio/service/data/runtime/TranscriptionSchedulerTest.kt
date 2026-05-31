/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.runtime

import app.altio.service.domain.job.Job
import app.altio.service.domain.job.JobRepository
import app.altio.service.domain.job.JobStatus
import app.altio.service.domain.job.JobType
import app.altio.service.domain.runtime.FinishReason
import app.altio.service.domain.runtime.InferenceChunk
import app.altio.service.domain.runtime.InferenceRequest
import app.altio.service.domain.runtime.Part
import app.altio.service.domain.runtime.RuntimeConfig
import app.altio.service.domain.runtime.RuntimeEngine
import app.altio.service.domain.runtime.RuntimeProvider
import app.altio.service.domain.runtime.RuntimeSession
import app.altio.service.domain.runtime.TokenUsage
import app.altio.service.domain.runtime.TranscriptionResult
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class TranscriptionSchedulerTest {

  private val jobs = mutableMapOf<String, Job>()
  private val completedOutputs = CopyOnWriteArrayList<Pair<String, String>>()

  private val jobRepository =
      object : JobRepository {
        override fun getJob(id: String) = kotlinx.coroutines.flow.flowOf(jobs[id])

        override fun observeAllJobs() = kotlinx.coroutines.flow.flowOf(jobs.values.toList())

        override fun observeActiveJobs() = kotlinx.coroutines.flow.flowOf(jobs.values.toList())

        override fun observeActiveJobCount() = kotlinx.coroutines.flow.flowOf(0)

        override suspend fun createJob(job: Job): Job {
          jobs[job.id] = job
          return job
        }

        override suspend fun updateStarted(id: String, startedAt: Instant) {
          jobs[id] = jobs[id]!!.copy(status = JobStatus.RUNNING, startedAt = startedAt)
        }

        override suspend fun complete(id: String, output: String, completedAt: Instant) {
          jobs[id] = jobs[id]!!.copy(status = JobStatus.COMPLETED, output = output)
          completedOutputs.add(id to output)
        }

        override suspend fun fail(id: String, errorCode: String, completedAt: Instant) {
          jobs[id] = jobs[id]!!.copy(status = JobStatus.FAILED, errorCode = errorCode)
        }

        override suspend fun cancel(id: String, completedAt: Instant) {
          jobs[id] = jobs[id]!!.copy(status = JobStatus.CANCELLED)
        }

        override suspend fun failActiveJobsOnStartup(completedAt: Instant): Int = 0

        override suspend fun countActiveByClient(clientId: String): Int = 0
      }

  @Test
  fun `transcription job routes through generateStream and stores plain transcript text`() =
      runTest {
        val sessionId = "ses-transcribe-1"
        val expectedTranscript = "Hello, this is a test recording."
        val fakeSession =
            TranscriptionFakeSession(sessionId = sessionId, transcript = expectedTranscript)
        val sessionManager = fakeSessionManager(sessionId, fakeSession)
        val scheduler = InferenceScheduler(backgroundScope, sessionManager, jobRepository)

        val jobId = "job-tr-1"
        jobs[jobId] =
            Job(
                id = jobId,
                sessionId = sessionId,
                clientId = "test",
                appName = null,
                type = JobType.TRANSCRIBE,
                status = JobStatus.QUEUED,
                createdAt = Instant.now(),
            )
        scheduler.enqueue(SchedulerWork.Transcribe(jobId, sessionId, ByteArray(0), "audio/mpeg"))

        kotlinx.coroutines.delay(200)

        val output = completedOutputs.firstOrNull()?.second
        assertNotNull(output)
        assertEquals(expectedTranscript, output)
      }

  @Test
  fun `transcription job passes Part_Audio in the user message`() = runTest {
    val sessionId = "ses-transcribe-2"
    val audioBytes = byteArrayOf(1, 2, 3)
    val capturedParts = CopyOnWriteArrayList<app.altio.service.domain.runtime.Part>()
    val fakeSession =
        object : RuntimeSession {
          override val sessionId = sessionId

          override fun generateStream(request: InferenceRequest): Flow<InferenceChunk> {
            request.messages.forEach { m -> capturedParts.addAll(m.parts) }
            return flow { emit(InferenceChunk.Done(FinishReason.STOP, TokenUsage(0, 0))) }
          }

          override suspend fun transcribe(
              audioBytes: ByteArray,
              mimeType: String,
          ): TranscriptionResult = throw UnsupportedOperationException()

          override suspend fun reset() {}

          override fun close() {}
        }
    val sessionManager = fakeSessionManager(sessionId, fakeSession)
    val scheduler = InferenceScheduler(backgroundScope, sessionManager, jobRepository)

    val jobId = "job-tr-2"
    jobs[jobId] =
        Job(
            id = jobId,
            sessionId = sessionId,
            clientId = "test",
            appName = null,
            type = JobType.TRANSCRIBE,
            status = JobStatus.QUEUED,
            createdAt = Instant.now(),
        )
    scheduler.enqueue(SchedulerWork.Transcribe(jobId, sessionId, audioBytes, "audio/mpeg"))

    kotlinx.coroutines.delay(200)

    val audioPart = capturedParts.filterIsInstance<Part.Audio>().firstOrNull()
    assertNotNull(audioPart)
    assert(audioPart!!.rawBytes.contentEquals(audioBytes))
  }

  private fun fakeSessionManager(
      expectedSessionId: String,
      session: RuntimeSession,
  ): RuntimeSessionManager {
    val holder =
        RuntimeEngineHolder(
            object : RuntimeProvider {
              override fun supports(model: app.altio.service.domain.model.Model) = false

              override suspend fun load(
                  model: app.altio.service.domain.model.Model,
                  config: RuntimeConfig,
              ): RuntimeEngine = throw UnsupportedOperationException()
            }
        )
    return object : RuntimeSessionManager(holder) {
      override fun getSession(sessionId: String): RuntimeSession? =
          if (sessionId == expectedSessionId) session else null
    }
  }
}

private class TranscriptionFakeSession(
    override val sessionId: String,
    private val transcript: String,
) : RuntimeSession {
  override fun generateStream(request: InferenceRequest): Flow<InferenceChunk> = flow {
    emit(InferenceChunk.Token(text = transcript, index = 0))
    emit(InferenceChunk.Done(FinishReason.STOP, TokenUsage(promptTokens = 0, completionTokens = 1)))
  }

  override suspend fun transcribe(audioBytes: ByteArray, mimeType: String): TranscriptionResult =
      throw UnsupportedOperationException()

  override suspend fun reset() {}

  override fun close() {}
}
