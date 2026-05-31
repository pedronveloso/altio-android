/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.contract.stream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FinishReason {
  @SerialName("stop") STOP,
  @SerialName("max_tokens") MAX_TOKENS,
  @SerialName("cancelled") CANCELLED,
  @SerialName("error") ERROR,
}
