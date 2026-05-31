/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.contract.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateSessionRequest(
    @SerialName("model_id") val modelId: String? = null,
    @SerialName("app_name") val appName: String? = null,
    @SerialName("system_prompt") val systemPrompt: String? = null,
    val temperature: Float = 1.0f,
    @SerialName("top_k") val topK: Int = 64,
    @SerialName("top_p") val topP: Float = 0.95f,
    @SerialName("max_tokens") val maxTokens: Int = 4000,
)
