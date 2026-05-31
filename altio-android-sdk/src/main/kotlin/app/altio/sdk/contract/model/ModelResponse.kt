/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.contract.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelResponse(
    val id: String,
    val name: String,
    val description: String,
    val source: ModelSource,
    val status: ModelStatus,
    @SerialName("size_bytes") val sizeBytes: Long,
    val capabilities: List<ModelCapability>,
    @SerialName("file_path") val filePath: String?,
    @SerialName("downloaded_at") val downloadedAt: Long?,
)
