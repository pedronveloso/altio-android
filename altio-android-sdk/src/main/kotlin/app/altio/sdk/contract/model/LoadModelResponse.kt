/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.contract.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoadModelResponse(
    @SerialName("model_id") val modelId: String,
    val status: ModelStatus,
)
