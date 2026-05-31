/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.runtime

import app.altio.service.domain.job.Job
import app.altio.service.domain.job.JobRepository
import app.altio.service.domain.job.JobStatus
import app.altio.service.domain.job.JobType
import app.altio.service.domain.runtime.InferenceChunk
import app.altio.service.domain.runtime.InferenceRequest
import app.altio.service.domain.runtime.Message
import app.altio.service.domain.runtime.Part
import app.altio.service.domain.runtime.Role
import app.altio.service.domain.runtime.RuntimeConfig
import app.altio.service.domain.runtime.RuntimeEngine
import app.altio.service.domain.runtime.RuntimeProvider
import app.altio.service.domain.runtime.RuntimeSession
import app.altio.service.domain.runtime.TranscriptionResult
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CancelJobTest {

  private val jobs = mutableMapOf<String, Job>()
  private val cancelledJobIds = CopyOnWriteArrayList<String>()
  private val startedJobIds = CopyOnWriteArrayList<String>()

  private val jobRepository =
      object : JobRepository {
        override fun getJob(id: String) = flowOf(jobs[id])

        override fun observeAllJobs() = flowOf(jobs.values.toList())

        override fun observeActiveJobs() = flowOf(jobs.values.toList())

        override fun observeActiveJobCount() = flowOf(0)

        override suspend fun createJob(job: Job): Job {
          jobs[job.id] = job
          return job
        }

        override suspend fun updateStarted(id: String, startedAt: Instant) {
          jobs[id] = jobs[id]!!.copy(status = JobStatus.RUNNING, startedAt = startedAt)
          startedJobIds.add(id)
        }

        override suspend fun complete(id: String, output: String, completedAt: Instant) {
          jobs[id] = jobs[id]!!.copy(status = JobStatus.COMPLETED, output = output)
        }

        override suspend fun fail(id: String, errorCode: String, completedAt: Instant) {
          jobs[id] = jobs[id]!!.copy(status = JobStatus.FAILED, errorCode = errorCode)
        }

        override suspend fun cancel(id: String, completedAt: Instant) {
          jobs[id] = jobs[id]!!.copy(status = JobStatus.CANCELLED)
          cancelledJobIds.add(id)
        }

        override suspend fun failActiveJobsOnStartup(completedAt: Instant): Int = 0

        override suspend fun countActiveByClient(clientId: String): Int = 0
      }

  @Test
  fun `cancelled running job transitions to CANCELLED status`() = runTest {
    val sessionId = UUID.randomUUID().toString()
    // Session that blocks indefinitely — simulates slow inference.
    val blockingSession = BlockingRuntimeSession(sessionId)
    val sessionManager = fakeSessionManager(sessionId, blockingSession)
    val scheduler = InferenceScheduler(backgroundScope, sessionManager, jobRepository)

    val jobId = "job-cancel-1"
    jobs[jobId] = makeJob(jobId, sessionId)
    scheduler.enqueue(SchedulerWork.Generate(jobId, sessionId, makeRequest("Hi")))

    // Give the scheduler time to start processing.
    delay(100)

    scheduler.cancelJob(jobId)

    delay(200)

    assertEquals(JobStatus.CANCELLED, jobs[jobId]?.status)
    assertEquals(true, cancelledJobIds.contains(jobId))
  }

  @Test
  fun `cancelling a queued job that has not started marks it CANCELLED`() = runTest {
    val sessionId = UUID.randomUUID().toString()
    val blockingSession = BlockingRuntimeSession(sessionId)
    val sessionManager = fakeSessionManager(sessionId, blockingSession)
    val scheduler = InferenceScheduler(backgroundScope, sessionManager, jobRepository)

    // job-1 occupies the scheduler.
    val job1Id = "job-queue-1"
    jobs[job1Id] = makeJob(job1Id, sessionId)
    scheduler.enqueue(SchedulerWork.Generate(job1Id, sessionId, makeRequest("first")))

    // job-2 is queued behind job-1.
    val job2Id = "job-queue-2"
    jobs[job2Id] = makeJob(job2Id, sessionId)
    scheduler.enqueue(SchedulerWork.Generate(job2Id, sessionId, makeRequest("second")))

    delay(50) // Let job-1 start running.

    // Cancel job-2 while it is still in the queue.
    scheduler.cancelJob(job2Id)
    delay(200)

    assertEquals(JobStatus.CANCELLED, jobs[job2Id]?.status)
    assertEquals(false, startedJobIds.contains(job2Id))
  }

  @Test
  fun `cancelling a queued job emits terminal cancellation chunk`() = runTest {
    val sessionId = UUID.randomUUID().toString()
    val blockingSession = BlockingRuntimeSession(sessionId)
    val sessionManager = fakeSessionManager(sessionId, blockingSession)
    val scheduler = InferenceScheduler(backgroundScope, sessionManager, jobRepository)

    val job1Id = "job-queue-stream-1"
    jobs[job1Id] = makeJob(job1Id, sessionId)
    scheduler.enqueue(SchedulerWork.Generate(job1Id, sessionId, makeRequest("first")))

    val job2Id = "job-queue-stream-2"
    jobs[job2Id] = makeJob(job2Id, sessionId)
    scheduler.enqueue(SchedulerWork.Generate(job2Id, sessionId, makeRequest("second")))

    delay(50)

    scheduler.cancelJob(job2Id)
    delay(200)

    val replayCache = scheduler.streamJob(job2Id)?.replayCache.orEmpty()
    assertEquals(1, replayCache.size)
    val terminalChunk = replayCache.single()
    assertTrue(terminalChunk is InferenceChunk.Error)
    assertEquals("CANCELLED", (terminalChunk as InferenceChunk.Error).cause.message)
  }

  private fun makeJob(id: String, sessionId: String) =
      Job(
          id = id,
          sessionId = sessionId,
          clientId = "test",
          appName = null,
          type = JobType.GENERATE,
          status = JobStatus.QUEUED,
          createdAt = Instant.now(),
      )

  private fun makeRequest(text: String) =
      InferenceRequest(listOf(Message(Role.USER, listOf(Part.Text(text)))))

  private fun fakeSessionManager(
      sessionId: String,
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
          if (sessionId == session.sessionId) session else null
    }
  }
}

/** A fake session whose [generateStream] never completes — used to simulate slow inference. */
private class BlockingRuntimeSession(override val sessionId: String) : RuntimeSession {
  override fun generateStream(request: InferenceRequest): Flow<InferenceChunk> = callbackFlow {
    // Emit one token then block indefinitely.
    trySend(InferenceChunk.Token("...", 0))
    awaitClose { /* nothing to clean up */ }
  }

  override suspend fun transcribe(audioBytes: ByteArray, mimeType: String): TranscriptionResult =
      throw UnsupportedOperationException()

  override suspend fun reset() {}

  override fun close() {}
}
