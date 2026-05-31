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
import app.altio.service.domain.runtime.Message
import app.altio.service.domain.runtime.Part
import app.altio.service.domain.runtime.Role
import app.altio.service.domain.runtime.RuntimeConfig
import app.altio.service.domain.runtime.RuntimeEngine
import app.altio.service.domain.runtime.RuntimeProvider
import app.altio.service.domain.runtime.RuntimeSession
import app.altio.service.domain.runtime.TokenUsage
import app.altio.service.domain.runtime.TranscriptionResult
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InferenceSchedulerTest {

  private val jobs = mutableMapOf<String, Job>()
  private val completedOutputs = CopyOnWriteArrayList<Pair<String, String>>()
  private val failedJobs = CopyOnWriteArrayList<Pair<String, String>>()

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
          failedJobs.add(id to errorCode)
        }

        override suspend fun cancel(id: String, completedAt: Instant) {
          jobs[id] = jobs[id]!!.copy(status = JobStatus.CANCELLED)
        }

        override suspend fun failActiveJobsOnStartup(completedAt: Instant): Int = 0

        override suspend fun countActiveByClient(clientId: String): Int = 0
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

  @Test
  fun `completes job with concatenated token output`() = runTest {
    val sessionId = UUID.randomUUID().toString()
    val fakeSession =
        FakeRuntimeSession(
            sessionId = sessionId,
            chunks =
                listOf(
                    InferenceChunk.Token("Hello", 0),
                    InferenceChunk.Token(" world", 1),
                    InferenceChunk.Done(FinishReason.STOP, TokenUsage(10, 2)),
                ),
        )
    val sessionManager = fakeSessionManager(sessionId, fakeSession)
    // backgroundScope does not cause UncompletedCoroutinesError on runTest exit
    val scheduler = InferenceScheduler(backgroundScope, sessionManager, jobRepository)

    val jobId = "job-1"
    jobs[jobId] = makeJob(jobId, sessionId)
    scheduler.enqueue(SchedulerWork.Generate(jobId, sessionId, makeRequest("Hi")))

    kotlinx.coroutines.delay(200)

    assertEquals("Hello world", completedOutputs.firstOrNull()?.second)
  }

  @Test
  fun `fails job when session not found`() = runTest {
    val sessionManager = emptySessionManager()
    val scheduler = InferenceScheduler(backgroundScope, sessionManager, jobRepository)

    val jobId = "job-2"
    val sessionId = "ghost-session"
    jobs[jobId] = makeJob(jobId, sessionId)
    scheduler.enqueue(SchedulerWork.Generate(jobId, sessionId, makeRequest("Hi")))

    kotlinx.coroutines.delay(200)

    assertEquals("SESSION_NOT_FOUND", failedJobs.firstOrNull()?.second)
  }

  @Test
  fun `fails job when session throws during inference`() = runTest {
    val sessionId = UUID.randomUUID().toString()
    val fakeSession =
        FakeRuntimeSession(
            sessionId = sessionId,
            chunks = listOf(InferenceChunk.Error(RuntimeException("GPU OOM"))),
        )
    val sessionManager = fakeSessionManager(sessionId, fakeSession)
    val scheduler = InferenceScheduler(backgroundScope, sessionManager, jobRepository)

    val jobId = "job-3"
    jobs[jobId] = makeJob(jobId, sessionId)
    scheduler.enqueue(SchedulerWork.Generate(jobId, sessionId, makeRequest("Hi")))

    kotlinx.coroutines.delay(200)

    assertEquals(true, failedJobs.any { it.first == jobId })
  }

  @Test
  fun `completed jobs retain bounded replay for late subscribers`() = runTest {
    val sessionId = UUID.randomUUID().toString()
    val terminalChunk = InferenceChunk.Done(FinishReason.STOP, TokenUsage(10, 2))
    val fakeSession =
        FakeRuntimeSession(
            sessionId = sessionId,
            chunks =
                listOf(
                    InferenceChunk.Token("Hello", 0),
                    InferenceChunk.Token(" world", 1),
                    terminalChunk,
                ),
        )
    val sessionManager = fakeSessionManager(sessionId, fakeSession)
    val scheduler = InferenceScheduler(backgroundScope, sessionManager, jobRepository)

    val jobId = "job-replay-1"
    jobs[jobId] = makeJob(jobId, sessionId)
    scheduler.enqueue(SchedulerWork.Generate(jobId, sessionId, makeRequest("Hi")))

    kotlinx.coroutines.delay(200)

    val stream = scheduler.streamJob(jobId)!!
    assertEquals(
        listOf(
            InferenceChunk.Token("Hello", 0),
            InferenceChunk.Token(" world", 1),
            terminalChunk,
        ),
        stream.replayCache,
    )
  }

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
    val manager = RuntimeSessionManager(holder)
    // Inject session directly via reflection isn't clean; use a subclass workaround.
    // Instead, use our own fake manager implementation.
    return object : RuntimeSessionManager(holder) {
      override fun getSession(sessionId: String): RuntimeSession? =
          if (sessionId == session.sessionId) session else null
    }
  }

  private fun emptySessionManager(): RuntimeSessionManager {
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
    return RuntimeSessionManager(holder)
  }
}

private class FakeRuntimeSession(
    override val sessionId: String,
    private val chunks: List<InferenceChunk>,
) : RuntimeSession {

  override fun generateStream(request: InferenceRequest): Flow<InferenceChunk> = flow {
    for (chunk in chunks) {
      emit(chunk)
      if (chunk is InferenceChunk.Error) throw chunk.cause
    }
  }

  override suspend fun transcribe(audioBytes: ByteArray, mimeType: String): TranscriptionResult =
      throw UnsupportedOperationException()

  override suspend fun reset() {}

  override fun close() {}
}
