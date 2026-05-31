/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.contract.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ModelSource {
  @SerialName("downloaded") DOWNLOADED,
  @SerialName("built_in") BUILT_IN,
}
