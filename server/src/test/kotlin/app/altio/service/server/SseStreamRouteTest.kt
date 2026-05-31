/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server

import app.altio.service.data.runtime.InferenceScheduler
import app.altio.service.domain.job.Job
import app.altio.service.domain.job.JobRepository
import app.altio.service.domain.job.JobStatus
import app.altio.service.domain.job.JobType
import app.altio.service.domain.runtime.FinishReason
import app.altio.service.domain.runtime.InferenceChunk
import app.altio.service.domain.runtime.TokenUsage
import app.altio.service.server.plugins.configureAuth
import app.altio.service.server.plugins.configureRouting
import app.altio.service.server.plugins.configureSerialization
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Matches the clientId returned by stubServerDependencies()'s fake tokenRepository.
private const val TEST_CLIENT_ID = "client_test"

class SseStreamRouteTest {

  @Test
  fun `SSE stream emits token events then done event`() = testApplication {
    val flow = MutableSharedFlow<InferenceChunk>(replay = Int.MAX_VALUE)
    flow.tryEmit(InferenceChunk.Token("Hello", 0))
    flow.tryEmit(InferenceChunk.Token(" world", 1))
    flow.tryEmit(InferenceChunk.Done(FinishReason.STOP, TokenUsage(5, 2)))

    val deps = stubServerDependencies().withScheduler("job-sse-1", flow)
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response =
        client.get("/v1/jobs/job-sse-1/stream") {
          accept(ContentType("text", "event-stream"))
          bearerAuth("test-token")
        }

    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.bodyAsText()
    assertTrue(body.contains("event: token"), "Expected token events in: $body")
    assertTrue(body.contains("event: done"), "Expected done event in: $body")
    assertTrue(body.contains("Hello"), "Expected first token text in: $body")
    assertTrue(body.contains("world"), "Expected second token text in: $body")
  }

  @Test
  fun `SSE stream emits error event for unknown job`() = testApplication {
    val deps = stubServerDependencies()
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response =
        client.get("/v1/jobs/nonexistent/stream") {
          accept(ContentType("text", "event-stream"))
          bearerAuth("test-token")
        }

    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.bodyAsText()
    assertTrue(body.contains("event: error"), "Expected error event in: $body")
  }

  @Test
  fun `SSE stream emits error event when inference fails mid-stream`() = testApplication {
    val flow = MutableSharedFlow<InferenceChunk>(replay = Int.MAX_VALUE)
    flow.tryEmit(InferenceChunk.Token("Partial", 0))
    flow.tryEmit(InferenceChunk.Error(RuntimeException("GPU OOM")))

    val deps = stubServerDependencies().withScheduler("job-err-1", flow)
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response =
        client.get("/v1/jobs/job-err-1/stream") {
          accept(ContentType("text", "event-stream"))
          bearerAuth("test-token")
        }

    val body = response.bodyAsText()
    assertTrue(body.contains("event: token"), "Expected partial token in: $body")
    assertTrue(body.contains("event: error"), "Expected error event in: $body")
  }
}

private fun ServerDependencies.withScheduler(
    targetJobId: String,
    flow: SharedFlow<InferenceChunk>,
): ServerDependencies {
  val sm = sessionManager
  val jr = jobRepository

  // Fake job repo: return an owned job for the target ID so ownership check passes.
  val ownedJob =
      Job(
          id = targetJobId,
          sessionId = "ses-test",
          clientId = TEST_CLIENT_ID,
          appName = null,
          type = JobType.GENERATE,
          status = JobStatus.RUNNING,
          createdAt = Instant.now(),
      )
  val fakeJobRepo =
      object : JobRepository by jr {
        override fun getJob(id: String): Flow<Job?> =
            flowOf(if (id == targetJobId) ownedJob else null)
      }

  val fakeScheduler =
      object :
          InferenceScheduler(
              scope = CoroutineScope(Dispatchers.Default),
              sessionManager = sm,
              jobRepository = fakeJobRepo,
          ) {
        override fun streamJob(jobId: String): SharedFlow<InferenceChunk>? =
            if (jobId == targetJobId) flow else null
      }
  return copy(inferenceScheduler = fakeScheduler, jobRepository = fakeJobRepo)
}
