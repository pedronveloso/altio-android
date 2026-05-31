/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server

import app.altio.service.domain.runtime.GenerationConfig
import app.altio.service.domain.session.Session
import app.altio.service.domain.session.SessionRepository
import app.altio.service.server.plugins.configureAuth
import app.altio.service.server.plugins.configureRouting
import app.altio.service.server.plugins.configureSerialization
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenerateRouteTest {

  private val ownSession = makeSession("ses-gen-1", clientId = "client_test")

  @Test
  fun `POST generate returns 202 with job_id`() = testApplication {
    val deps = stubServerDependencies().withOwnSession(ownSession)
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response =
        client.post("/v1/sessions/ses-gen-1/generate") {
          bearerAuth("test-token")
          header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
          setBody("""{"messages":[{"role":"user","content":"Hello"}]}""")
        }
    assertEquals(HttpStatusCode.Accepted, response.status)
    val body = response.bodyAsText()
    assertTrue(body.contains("job_id"), "Expected job_id in: $body")
  }

  @Test
  fun `POST generate without auth returns 401`() = testApplication {
    val deps = stubServerDependencies().withOwnSession(ownSession)
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response =
        client.post("/v1/sessions/ses-gen-1/generate") {
          header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
          setBody("""{"messages":[{"role":"user","content":"Hello"}]}""")
        }
    assertEquals(HttpStatusCode.Unauthorized, response.status)
  }

  @Test
  fun `POST generate for unknown session returns 404`() = testApplication {
    val deps = stubServerDependencies() // no sessions registered
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response =
        client.post("/v1/sessions/nonexistent/generate") {
          bearerAuth("test-token")
          header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
          setBody("""{"messages":[{"role":"user","content":"Hello"}]}""")
        }
    assertEquals(HttpStatusCode.NotFound, response.status)
  }

  @Test
  fun `POST generate with oversized body returns 413`() = testApplication {
    val deps = stubServerDependencies().withOwnSession(ownSession)
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    // Send a body that actually exceeds 1 MB so Content-Length is set correctly.
    val largeContent = "x".repeat(1 * 1024 * 1024 + 1)
    val response =
        client.post("/v1/sessions/ses-gen-1/generate") {
          bearerAuth("test-token")
          header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
          setBody("""{"messages":[{"role":"user","content":"$largeContent"}]}""")
        }
    assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    val body = response.bodyAsText()
    assertTrue(body.contains("REQUEST_TOO_LARGE"), "Expected REQUEST_TOO_LARGE in: $body")
  }

  @Test
  fun `POST generate response body contains queued status`() = testApplication {
    val deps = stubServerDependencies().withOwnSession(ownSession)
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val body =
        client
            .post("/v1/sessions/ses-gen-1/generate") {
              bearerAuth("test-token")
              header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
              setBody("""{"messages":[{"role":"user","content":"Hello"}]}""")
            }
            .bodyAsText()
    assertTrue(body.contains("\"status\":\"queued\""), "Expected queued status in: $body")
  }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

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

private fun ServerDependencies.withOwnSession(session: Session): ServerDependencies {
  val fakeRepo =
      object : SessionRepository by sessionRepository {
        override fun getSession(id: String): Flow<Session?> =
            flowOf(if (id == session.id) session else null)
      }
  return copy(sessionRepository = fakeRepo)
}
