/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server

import app.altio.service.domain.job.Job
import app.altio.service.domain.job.JobRepository
import app.altio.service.domain.job.JobStatus
import app.altio.service.domain.job.JobType
import app.altio.service.domain.runtime.GenerationConfig
import app.altio.service.domain.session.Session
import app.altio.service.domain.session.SessionRepository
import app.altio.service.domain.token.ClientToken
import app.altio.service.domain.token.TokenRepository
import app.altio.service.server.plugins.configureAuth
import app.altio.service.server.plugins.configureRouting
import app.altio.service.server.plugins.configureSerialization
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecurityTest {

  // ─── 401 Unauthorized ────────────────────────────────────────────────────

  @Test
  fun `missing bearer token returns 401`() = testApplication {
    val deps = stubServerDependencies()
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response = client.get("/v1/sessions/any-session")
    assertEquals(HttpStatusCode.Unauthorized, response.status)
  }

  @Test
  fun `invalid bearer token returns 401`() = testApplication {
    val deps = stubServerDependencies().withRejectingTokenRepo()
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response = client.get("/v1/sessions/any-session") { bearerAuth("bad-token") }
    assertEquals(HttpStatusCode.Unauthorized, response.status)
  }

  @Test
  fun `health endpoint is accessible without a token`() = testApplication {
    val deps = stubServerDependencies().withRejectingTokenRepo()
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response = client.get("/v1/health")
    assertEquals(HttpStatusCode.OK, response.status)
  }

  // ─── 404 SESSION_NOT_FOUND for wrong-client access ───────────────────────

  @Test
  fun `accessing another client's session returns 404 SESSION_NOT_FOUND`() = testApplication {
    // Session belongs to "client_other", but the token authenticates as "client_test".
    val otherSession = makeSession(id = "ses-other", clientId = "client_other")
    val deps = stubServerDependencies().withFakeSession(otherSession)
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response = client.get("/v1/sessions/ses-other") { bearerAuth("test-token") }
    assertEquals(HttpStatusCode.NotFound, response.status)
    val body = response.bodyAsText()
    assertTrue(body.contains("SESSION_NOT_FOUND"), "Expected SESSION_NOT_FOUND in: $body")
  }

  @Test
  fun `accessing another client's job via cancel returns 404 JOB_NOT_FOUND`() = testApplication {
    val otherJob = makeJob(id = "job-other", clientId = "client_other")
    val deps = stubServerDependencies().withFakeJob(otherJob)
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response = client.post("/v1/jobs/job-other/cancel") { bearerAuth("test-token") }
    assertEquals(HttpStatusCode.NotFound, response.status)
  }

  // ─── 429 Rate limiting ────────────────────────────────────────────────────

  @Test
  fun `exceeding active job limit returns 429 RATE_LIMITED`() = testApplication {
    val ownSession = makeSession(id = "ses-rate", clientId = "client_test")
    val deps =
        stubServerDependencies()
            .withFakeSession(ownSession)
            .withJobCount(activeCount = 5) // at the cap
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response =
        client.post("/v1/sessions/ses-rate/generate") {
          bearerAuth("test-token")
          contentType(ContentType.Application.Json)
          setBody("""{"messages":[{"role":"user","content":"hi"}]}""")
        }
    assertEquals(HttpStatusCode.TooManyRequests, response.status)
    val body = response.bodyAsText()
    assertTrue(body.contains("RATE_LIMITED"), "Expected RATE_LIMITED in: $body")
  }

  @Test
  fun `exceeding session limit returns 429 RATE_LIMITED`() = testApplication {
    val deps = stubServerDependencies().withSessionCount(count = 20) // at the cap
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response =
        client.post("/v1/sessions") {
          bearerAuth("test-token")
          contentType(ContentType.Application.Json)
          setBody("""{"model_id":"gemma-3n-e2b-it-int4"}""")
        }
    assertEquals(HttpStatusCode.TooManyRequests, response.status)
    val body = response.bodyAsText()
    assertTrue(body.contains("RATE_LIMITED"), "Expected RATE_LIMITED in: $body")
  }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun ServerDependencies.withRejectingTokenRepo(): ServerDependencies {
  val rejectAll =
      object : TokenRepository {
        override suspend fun generateToken(label: String): String = ""

        override suspend fun validateToken(rawToken: String): ClientToken? = null

        override suspend fun revokeToken(tokenHash: String) {}

        override fun observeTokens() = kotlinx.coroutines.flow.emptyFlow<List<ClientToken>>()
      }
  return copy(tokenRepository = rejectAll)
}

private fun ServerDependencies.withFakeSession(session: Session): ServerDependencies {
  val fakeRepo =
      object : SessionRepository by sessionRepository {
        override fun getSession(id: String): Flow<Session?> =
            flowOf(if (id == session.id) session else null)
      }
  return copy(sessionRepository = fakeRepo)
}

private fun ServerDependencies.withFakeJob(job: Job): ServerDependencies {
  val fakeRepo =
      object : JobRepository by jobRepository {
        override fun getJob(id: String): Flow<Job?> = flowOf(if (id == job.id) job else null)
      }
  return copy(jobRepository = fakeRepo)
}

private fun ServerDependencies.withJobCount(activeCount: Int): ServerDependencies {
  val fakeRepo =
      object : JobRepository by jobRepository {
        override suspend fun countActiveByClient(clientId: String): Int = activeCount
      }
  return copy(jobRepository = fakeRepo)
}

private fun ServerDependencies.withSessionCount(count: Int): ServerDependencies {
  val fakeRepo =
      object : SessionRepository by sessionRepository {
        override suspend fun countByClient(clientId: String): Int = count
      }
  return copy(sessionRepository = fakeRepo)
}

private fun makeSession(id: String, clientId: String) =
    Session(
        id = id,
        clientId = clientId,
        appName = null,
        modelId = "gemma-3n",
        systemPrompt = null,
        generationConfig = GenerationConfig(),
        messageCount = 0,
        createdAt = Instant.now(),
        lastActiveAt = Instant.now(),
    )

private fun makeJob(id: String, clientId: String) =
    Job(
        id = id,
        sessionId = "ses-test",
        clientId = clientId,
        appName = null,
        type = JobType.GENERATE,
        status = JobStatus.RUNNING,
        createdAt = Instant.now(),
    )
