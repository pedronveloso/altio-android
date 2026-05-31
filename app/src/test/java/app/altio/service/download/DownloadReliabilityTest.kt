/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.download

import app.altio.service.domain.model.DownloadProgress
import app.altio.service.domain.model.DownloadStatus
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadReliabilityTest {

  @Test
  fun `shows interruption warning for stalled active download`() {
    assertTrue(
        shouldShowInterruptionWarning(
            progress =
                progress(
                    status = DownloadStatus.DOWNLOADING,
                    bytesDownloaded = 1024,
                    bytesPerSec = 0,
                ),
            nowMs = 200_000,
            lastDownloadProgressAtMs = 10_000,
            stalledThresholdMs = 60_000,
        )
    )
  }

  @Test
  fun `does not show interruption warning when download still has speed`() {
    assertFalse(
        shouldShowInterruptionWarning(
            progress =
                progress(
                    status = DownloadStatus.DOWNLOADING,
                    bytesDownloaded = 1024,
                    bytesPerSec = 512,
                ),
            nowMs = 200_000,
            lastDownloadProgressAtMs = 10_000,
            stalledThresholdMs = 60_000,
        )
    )
  }

  @Test
  fun `does not show interruption warning before threshold elapses`() {
    assertFalse(
        shouldShowInterruptionWarning(
            progress =
                progress(status = DownloadStatus.QUEUED, bytesDownloaded = 1024, bytesPerSec = 0),
            nowMs = 50_000,
            lastDownloadProgressAtMs = 10_000,
            stalledThresholdMs = 60_000,
        )
    )
  }

  @Test
  fun `does not show interruption warning for terminal states`() {
    assertFalse(
        shouldShowInterruptionWarning(
            progress =
                progress(status = DownloadStatus.SUCCESS, bytesDownloaded = 1024, bytesPerSec = 0),
            nowMs = 200_000,
            lastDownloadProgressAtMs = 10_000,
            stalledThresholdMs = 60_000,
        )
    )
  }

  private fun progress(
      status: DownloadStatus,
      bytesDownloaded: Long,
      bytesPerSec: Long,
  ) =
      DownloadProgress(
          modelId = "test",
          bytesDownloaded = bytesDownloaded,
          totalBytes = 4096,
          bytesPerSec = bytesPerSec,
          etaMs = 0,
          status = status,
      )
}
