/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.contract.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ModelStatus {
  @SerialName("not_downloaded") NOT_DOWNLOADED,
  @SerialName("downloading") DOWNLOADING,
  @SerialName("paused") PAUSED,
  @SerialName("verifying") VERIFYING,
  @SerialName("ready") READY,
  @SerialName("loading") LOADING,
  @SerialName("loaded") LOADED;

  fun isReadyForSession() = this == READY || this == LOADED
}
