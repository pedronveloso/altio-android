/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.model

data class DownloadProgress(
    val modelId: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val bytesPerSec: Long,
    val etaMs: Long,
    val status: DownloadStatus,
    val failureReason: DownloadFailureReason? = null,
) {
  val fraction: Float
    get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
}
