/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.contract.job

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JobStatusResponse(
    @SerialName("job_id") val jobId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("app_name") val appName: String? = null,
    val status: JobStatus,
    val output: String? = null,
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("started_at") val startedAt: Long? = null,
    @SerialName("completed_at") val completedAt: Long? = null,
)
