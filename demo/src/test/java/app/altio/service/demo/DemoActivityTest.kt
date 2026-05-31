/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.demo

import java.net.ConnectException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class DemoActivityTest {

  @Test
  fun `captureConnectionError converts stale-port connect failures into recovery message`() =
      runTest {
        val error = captureConnectionError {
          throw ConnectException("Failed to connect to /127.0.0.1:46289")
        }

        assertEquals(
            "The service was not reachable on the saved port. Update the port and retry.",
            error,
        )
      }

  @Test
  fun `captureConnectionError returns null when connection succeeds`() = runTest {
    val error = captureConnectionError {}

    assertNull(error)
  }

  @Test
  fun `captureConnectionError rethrows cancellation`() = runTest {
    try {
      captureConnectionError { throw CancellationException("cancelled") }
      fail("Expected CancellationException to be rethrown")
    } catch (_: CancellationException) {
      // expected
    }
  }
}
