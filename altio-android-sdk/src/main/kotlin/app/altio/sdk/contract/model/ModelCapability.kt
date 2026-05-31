/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.contract.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ModelCapability {
  @SerialName("text") TEXT,
  @SerialName("vision") VISION,
  @SerialName("audio") AUDIO,
  @SerialName("thinking") THINKING,
}
