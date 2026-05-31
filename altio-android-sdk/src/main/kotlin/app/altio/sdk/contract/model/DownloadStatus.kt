/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.contract.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DownloadStatus {
  @SerialName("queued") QUEUED,
  @SerialName("downloading") DOWNLOADING,
  @SerialName("paused") PAUSED,
  @SerialName("verifying") VERIFYING,
  @SerialName("success") SUCCESS,
  @SerialName("failed") FAILED,
  @SerialName("cancelled") CANCELLED,
}
