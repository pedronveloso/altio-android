/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.contract.stream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DoneEvent(
    @SerialName("finish_reason") val finishReason: FinishReason,
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
)
