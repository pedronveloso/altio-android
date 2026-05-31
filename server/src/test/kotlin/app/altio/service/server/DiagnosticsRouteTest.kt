/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server

import app.altio.service.domain.job.JobRepository
import app.altio.service.domain.session.SessionRepository
import app.altio.service.server.plugins.configureAuth
import app.altio.service.server.plugins.configureRouting
import app.altio.service.server.plugins.configureSerialization
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiagnosticsRouteTest {

  @Test
  fun `GET v1 diagnostics returns 200 with auth`() = testApplication {
    val deps = stubServerDependencies()
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response = client.get("/v1/diagnostics") { bearerAuth("test-token") }
    assertEquals(HttpStatusCode.OK, response.status)
  }

  @Test
  fun `GET v1 diagnostics returns 401 without auth`() = testApplication {
    val deps = stubServerDependencies()
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response = client.get("/v1/diagnostics")
    assertEquals(HttpStatusCode.Unauthorized, response.status)
  }

  @Test
  fun `GET v1 diagnostics response contains uptime_seconds field`() = testApplication {
    val deps = stubServerDependencies()
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val body = client.get("/v1/diagnostics") { bearerAuth("test-token") }.bodyAsText()
    assertTrue(body.contains("uptime_seconds"), "Expected uptime_seconds in: $body")
  }

  @Test
  fun `GET v1 diagnostics response contains active_sessions field`() = testApplication {
    val deps = stubServerDependencies()
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val body = client.get("/v1/diagnostics") { bearerAuth("test-token") }.bodyAsText()
    assertTrue(body.contains("active_sessions"), "Expected active_sessions in: $body")
  }

  @Test
  fun `GET v1 diagnostics response contains active_jobs field`() = testApplication {
    val deps = stubServerDependencies()
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val body = client.get("/v1/diagnostics") { bearerAuth("test-token") }.bodyAsText()
    assertTrue(body.contains("active_jobs"), "Expected active_jobs in: $body")
  }

  @Test
  fun `GET v1 diagnostics returns correct session and job counts`() = testApplication {
    val fakeSessionRepo =
        object : SessionRepository by stubServerDependencies().sessionRepository {
          override suspend fun countByClient(clientId: String): Int = 3
        }
    val fakeJobRepo =
        object : JobRepository by stubServerDependencies().jobRepository {
          override suspend fun countActiveByClient(clientId: String): Int = 2
        }
    val deps =
        stubServerDependencies()
            .copy(
                sessionRepository = fakeSessionRepo,
                jobRepository = fakeJobRepo,
            )

    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      // Use a fixed start time in the past to get a predictable uptime
      configureRouting(deps, serverStartedAt = Instant.now().minusSeconds(60))
    }

    val body = client.get("/v1/diagnostics") { bearerAuth("test-token") }.bodyAsText()
    assertTrue(body.contains("\"active_sessions\":3"), "Expected active_sessions:3 in: $body")
    assertTrue(body.contains("\"active_jobs\":2"), "Expected active_jobs:2 in: $body")
  }

  @Test
  fun `GET v1 diagnostics uptime_seconds is non-negative`() = testApplication {
    val startedAt = Instant.now().minusSeconds(120)
    val deps = stubServerDependencies()

    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps, serverStartedAt = startedAt)
    }

    val body = client.get("/v1/diagnostics") { bearerAuth("test-token") }.bodyAsText()
    // Extract uptime_seconds value from JSON
    val regex = Regex(""""uptime_seconds":(\d+)""")
    val match = regex.find(body)
    assertTrue(match != null, "Expected uptime_seconds numeric value in: $body")
    val uptime = match!!.groupValues[1].toLong()
    assertTrue(uptime >= 0, "uptime_seconds must be non-negative, got $uptime")
  }
}
