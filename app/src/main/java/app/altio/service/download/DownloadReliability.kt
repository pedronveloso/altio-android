/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.download

import app.altio.service.domain.model.DownloadProgress
import app.altio.service.domain.model.DownloadStatus

internal fun shouldShowInterruptionWarning(
    progress: DownloadProgress?,
    nowMs: Long,
    lastDownloadProgressAtMs: Long,
    stalledThresholdMs: Long,
): Boolean {
  if (progress == null) return false
  if (progress.status != DownloadStatus.DOWNLOADING && progress.status != DownloadStatus.QUEUED) {
    return false
  }
  if (progress.bytesDownloaded <= 0L) return false
  if (progress.bytesPerSec > 0L) return false
  return nowMs - lastDownloadProgressAtMs >= stalledThresholdMs
}
