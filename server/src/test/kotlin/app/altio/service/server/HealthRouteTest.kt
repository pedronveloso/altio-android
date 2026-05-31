/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server

import app.altio.service.server.plugins.configureAuth
import app.altio.service.server.plugins.configureRouting
import app.altio.service.server.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HealthRouteTest {

  private val deps = stubServerDependencies()

  @Test
  fun `GET v1 health returns 200`() = testApplication {
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }
    val response = client.get("/v1/health")
    assertEquals(HttpStatusCode.OK, response.status)
  }

  @Test
  fun `GET v1 health returns ok status in body`() = testApplication {
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }
    val response = client.get("/v1/health")
    val body = response.bodyAsText()
    assertTrue(body.contains("\"status\":\"ok\""), "Expected status:ok in: $body")
  }

  @Test
  fun `GET v1 health returns version field`() = testApplication {
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }
    val response = client.get("/v1/health")
    val body = response.bodyAsText()
    assertTrue(body.contains("\"version\""), "Expected version field in: $body")
  }

  @Test
  fun `GET unknown path returns 404`() = testApplication {
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }
    val response = client.get("/v1/unknown")
    assertEquals(HttpStatusCode.NotFound, response.status)
  }
}
