/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.contract.generate

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MessageRole {
  @SerialName("system") SYSTEM,
  @SerialName("user") USER,
  @SerialName("assistant") ASSISTANT,
  @SerialName("model") MODEL,
}
