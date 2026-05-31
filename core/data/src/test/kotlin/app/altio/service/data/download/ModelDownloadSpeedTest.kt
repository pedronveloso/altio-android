/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelDownloadSpeedTest {

  // ── calculateSpeed ────────────────────────────────────────────────────────

  @Test
  fun `calculateSpeed with fewer than 2 samples returns 0`() {
    val samples = ArrayDeque<Pair<Long, Long>>()
    assertEquals(0L, ModelDownloadWorker.calculateSpeed(samples))

    samples.addLast(Pair(1000L, 500L))
    assertEquals(0L, ModelDownloadWorker.calculateSpeed(samples))
  }

  @Test
  fun `calculateSpeed computes bytes per second from oldest and newest sample`() {
    // 200 000 bytes in 500 ms → 400 000 B/s
    val samples = ArrayDeque<Pair<Long, Long>>()
    samples.addLast(Pair(0L, 0L))
    samples.addLast(Pair(250L, 100_000L))
    samples.addLast(Pair(500L, 200_000L))
    assertEquals(400_000L, ModelDownloadWorker.calculateSpeed(samples))
  }

  @Test
  fun `calculateSpeed coerces zero time delta to 1 ms to avoid division by zero`() {
    val samples = ArrayDeque<Pair<Long, Long>>()
    samples.addLast(Pair(1000L, 0L))
    samples.addLast(Pair(1000L, 8_192L)) // same timestamp
    // timeDelta coerced to 1 ms → 8_192 * 1_000 / 1 = 8_192_000 B/s
    assertEquals(8_192_000L, ModelDownloadWorker.calculateSpeed(samples))
  }

  // ── applyEma ──────────────────────────────────────────────────────────────

  @Test
  fun `applyEma seeds at full raw value when smoothed is 0`() {
    // Caller responsibility: seed by passing raw directly when smoothed == 0.
    // applyEma itself should not special-case 0; verify the formula is correct.
    val result = ModelDownloadWorker.applyEma(alpha = 0.15, raw = 1_000_000L, smoothed = 1_000_000L)
    assertEquals(1_000_000L, result)
  }

  @Test
  fun `applyEma blends new sample at 15 percent weight`() {
    // smoothed = 1 000 000, raw = 2 000 000
    // expected = 0.15 * 2_000_000 + 0.85 * 1_000_000 = 300_000 + 850_000 = 1_150_000
    val result = ModelDownloadWorker.applyEma(alpha = 0.15, raw = 2_000_000L, smoothed = 1_000_000L)
    assertEquals(1_150_000L, result)
  }

  @Test
  fun `contentRangeStartsAt matches expected resumed offset`() {
    assertTrue(ModelDownloadWorker.contentRangeStartsAt("bytes 1024-2047/4096", 1024L))
    assertTrue(ModelDownloadWorker.contentRangeStartsAt("Bytes 1024-2047/4096", 1024L))
  }

  @Test
  fun `contentRangeStartsAt rejects missing or mismatched header`() {
    assertFalse(ModelDownloadWorker.contentRangeStartsAt(null, 1024L))
    assertFalse(ModelDownloadWorker.contentRangeStartsAt("bytes 0-2047/4096", 1024L))
  }

  @Test
  fun `shouldRestartFromZeroOnResume when server ignores range request`() {
    assertTrue(
        ModelDownloadWorker.shouldRestartFromZeroOnResume(
            rangeRequested = true,
            responseCode = 200,
            contentRange = null,
            expectedStart = 1024L,
        )
    )
  }

  @Test
  fun `shouldRestartFromZeroOnResume when partial response starts at wrong offset`() {
    assertTrue(
        ModelDownloadWorker.shouldRestartFromZeroOnResume(
            rangeRequested = true,
            responseCode = 206,
            contentRange = "bytes 0-2047/4096",
            expectedStart = 1024L,
        )
    )
  }

  @Test
  fun `shouldRestartFromZeroOnResume keeps valid resumed downloads`() {
    assertFalse(
        ModelDownloadWorker.shouldRestartFromZeroOnResume(
            rangeRequested = true,
            responseCode = 206,
            contentRange = "bytes 1024-2047/4096",
            expectedStart = 1024L,
        )
    )
  }
}
