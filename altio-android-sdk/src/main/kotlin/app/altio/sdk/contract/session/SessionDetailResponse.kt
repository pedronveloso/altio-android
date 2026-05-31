/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.contract.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionDetailResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("model_id") val modelId: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("app_name") val appName: String? = null,
    @SerialName("system_prompt") val systemPrompt: String? = null,
    @SerialName("message_count") val messageCount: Int,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("last_active_at") val lastActiveAt: Long,
)
