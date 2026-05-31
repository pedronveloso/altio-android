/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.altio.service.domain.job.JobStatus
import app.altio.service.domain.job.JobType
import java.time.Instant

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val clientId: String,
    val appName: String?,
    val type: JobType,
    val status: JobStatus,
    val createdAt: Instant,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val output: String?,
    val errorCode: String?,
)
