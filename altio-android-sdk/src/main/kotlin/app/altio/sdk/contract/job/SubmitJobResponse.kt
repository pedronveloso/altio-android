/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.contract.job

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubmitJobResponse(
    @SerialName("job_id") val jobId: String,
    val status: JobStatus,
)
