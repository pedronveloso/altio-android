/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service

import app.altio.service.state.ServiceStartFailure
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AiBackgroundServiceNotificationTest {

  @Test
  fun notificationStatus_showsPortOnly_whenThereAreNoActiveSessions() {
    assertEquals("Running on port 52731", notificationStatus(port = 52731, activeSessions = 0))
  }

  @Test
  fun notificationStatus_showsSingularSessionCount() {
    assertEquals(
        "Running on port 52731 • 1 active session",
        notificationStatus(port = 52731, activeSessions = 1),
    )
  }

  @Test
  fun notificationStatus_showsPluralSessionCount() {
    assertEquals(
        "Running on port 52731 • 3 active sessions",
        notificationStatus(port = 52731, activeSessions = 3),
    )
  }

  @Test
  fun notificationStatus_showsStopped_whenPortIsMissing() {
    assertEquals("Stopped", notificationStatus(port = null, activeSessions = 4))
  }

  @Test
  fun notificationStatus_showsFailureCodeAndTitle_whenStartFailureExists() {
    val failure =
        ServiceStartFailure(
            code = "SVC001",
            title = "Port unavailable",
            description = "Port 52731 is already in use.",
            attemptedPort = 52731,
            occurredAt = Instant.parse("2026-05-27T10:00:00Z"),
            technicalDetail = "java.net.BindException: Address already in use",
        )

    assertEquals(
        "SVC001: Port unavailable",
        notificationStatus(port = null, activeSessions = 0, startFailure = failure),
    )
  }
}
