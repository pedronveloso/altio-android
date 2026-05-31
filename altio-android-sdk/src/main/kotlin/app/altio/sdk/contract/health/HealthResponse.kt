/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.contract.health

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val version: String = "1.0.0",
    @SerialName("model_loaded") val modelLoaded: Boolean = false,
    @SerialName("active_sessions") val activeSessions: Int = 0,
    @SerialName("active_jobs") val activeJobs: Int = 0,
    @SerialName("uptime_ms") val uptimeMs: Long = 0L,
)
