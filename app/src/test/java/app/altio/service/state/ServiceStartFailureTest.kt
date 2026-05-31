/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.state

import app.altio.service.server.AiHttpServerStartException
import java.io.IOException
import java.net.BindException
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ServiceStartFailureTest {

  private val occurredAt = Instant.parse("2026-05-27T10:00:00Z")

  @Test
  fun `port already in use maps to SVC001`() {
    val failure =
        classifyServerStartFailure(
            BindException("Address already in use"),
            attemptedPort = 52731,
            occurredAt = occurredAt,
        )

    assertEquals("SVC001", failure.code)
    assertEquals("Port unavailable", failure.title)
    assertEquals(52731, failure.attemptedPort)
  }

  @Test
  fun `invalid port maps to SVC002`() {
    val failure =
        classifyServerStartFailure(
            AiHttpServerStartException(
                kind = AiHttpServerStartException.Kind.INVALID_PORT,
                attemptedPort = 70_000,
                message = "Invalid port",
            ),
            attemptedPort = 70_000,
            occurredAt = occurredAt,
        )

    assertEquals("SVC002", failure.code)
  }

  @Test
  fun `generic bind or socket error maps to SVC003`() {
    val failure =
        classifyServerStartFailure(
            IOException("Network is unreachable"),
            attemptedPort = 52731,
            occurredAt = occurredAt,
        )

    assertEquals("SVC003", failure.code)
  }

  @Test
  fun `unknown exception maps to SVC999`() {
    val failure =
        classifyServerStartFailure(
            IllegalStateException("Unexpected"),
            attemptedPort = 52731,
            occurredAt = occurredAt,
        )

    assertEquals("SVC999", failure.code)
    assertEquals("java.lang.IllegalStateException: Unexpected", failure.technicalDetail)
  }
}
