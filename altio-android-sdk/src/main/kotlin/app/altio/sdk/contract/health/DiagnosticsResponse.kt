/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.contract.health

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiagnosticsResponse(
    @SerialName("uptime_seconds") val uptimeSeconds: Long,
    @SerialName("active_sessions") val activeSessions: Int,
    @SerialName("active_jobs") val activeJobs: Int,
)
