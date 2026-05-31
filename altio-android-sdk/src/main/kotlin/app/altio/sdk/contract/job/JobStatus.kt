/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.contract.job

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class JobStatus {
  @SerialName("queued") QUEUED,
  @SerialName("running") RUNNING,
  @SerialName("completed") COMPLETED,
  @SerialName("failed") FAILED,
  @SerialName("cancelled") CANCELLED;

  fun isCancellable() = this == QUEUED || this == RUNNING

  fun isTerminal() = this == COMPLETED || this == FAILED || this == CANCELLED
}
