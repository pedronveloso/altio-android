/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.contract.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DownloadProgressResponse(
    @SerialName("model_id") val modelId: String,
    @SerialName("bytes_downloaded") val bytesDownloaded: Long,
    @SerialName("total_bytes") val totalBytes: Long,
    @SerialName("bytes_per_sec") val bytesPerSec: Long,
    @SerialName("eta_ms") val etaMs: Long,
    val fraction: Float,
    val status: DownloadStatus,
)
