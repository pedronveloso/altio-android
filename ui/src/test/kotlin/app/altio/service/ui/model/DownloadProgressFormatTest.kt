/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.ui.model

import app.altio.service.domain.model.DownloadProgress
import app.altio.service.domain.model.DownloadStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadProgressFormatTest {

  // ── formatEta ─────────────────────────────────────────────────────────────

  @Test
  fun `formatEta under 60 seconds shows seconds only`() {
    assertEquals("0s", formatEta(0L))
    assertEquals("1s", formatEta(1_000L))
    assertEquals("59s", formatEta(59_000L))
  }

  @Test
  fun `formatEta exactly 60 seconds shows 1m 0s`() {
    assertEquals("1m 0s", formatEta(60_000L))
  }

  @Test
  fun `formatEta minutes and seconds shows both components`() {
    assertEquals("2m 30s", formatEta(150_000L))
    assertEquals("59m 59s", formatEta(3_599_000L))
  }

  @Test
  fun `formatEta exactly 1 hour shows 1h 0m`() {
    assertEquals("1h 0m", formatEta(3_600_000L))
  }

  @Test
  fun `formatEta over 1 hour omits seconds`() {
    // 1 h 23 min 45 s → seconds are irrelevant at hour-scale
    assertEquals("1h 23m", formatEta(5_025_000L))
    assertEquals("2h 0m", formatEta(7_200_000L))
  }

  // ── formatProgress ────────────────────────────────────────────────────────

  @Test
  fun `formatProgress omits speed and ETA bullet points when both are zero`() {
    val progress =
        progress(bytesDownloaded = 0L, totalBytes = 1_048_576L, bytesPerSec = 0L, etaMs = 0L)
    val result = formatProgress(progress)
    assertFalse(result.contains("KB/s"), "Should not show speed when 0")
    assertFalse(result.contains("ETA"), "Should not show ETA when 0")
  }

  @Test
  fun `formatProgress includes speed and formatted ETA when present`() {
    // 5 MB/s = 5 * 1024 * 1024 B/s, total 100 MB, downloaded 50 MB, ETA 10 s
    val bytesPerSec = 5L * 1_024 * 1_024
    val progress =
        progress(
            bytesDownloaded = 50L * 1_048_576,
            totalBytes = 100L * 1_048_576,
            bytesPerSec = bytesPerSec,
            etaMs = 10_000L,
        )
    val result = formatProgress(progress)
    assertTrue(result.contains("KB/s"), "Should show speed")
    assertTrue(result.contains("ETA"), "Should show ETA label")
    assertTrue(result.contains("10s"), "Should show formatted ETA")
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private fun progress(
      bytesDownloaded: Long,
      totalBytes: Long,
      bytesPerSec: Long,
      etaMs: Long,
  ) =
      DownloadProgress(
          modelId = "test",
          bytesDownloaded = bytesDownloaded,
          totalBytes = totalBytes,
          bytesPerSec = bytesPerSec,
          etaMs = etaMs,
          status = DownloadStatus.DOWNLOADING,
      )
}
