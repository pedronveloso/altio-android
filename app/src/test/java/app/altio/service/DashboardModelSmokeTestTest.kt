/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DashboardModelSmokeTestTest {

  @Test
  fun `dashboard smoke test guard allows test when no sessions are active`() {
    checkDashboardModelSmokeTestAvailable(activeSessionCount = 0)
  }

  @Test
  fun `dashboard smoke test guard rejects test when a session is active`() {
    val error =
        assertThrows(IllegalStateException::class.java) {
          checkDashboardModelSmokeTestAvailable(activeSessionCount = 1)
        }

    assertEquals("Close active sessions before testing the model.", error.message)
  }
}
