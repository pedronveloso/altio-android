/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.job

import java.time.Instant

data class Job(
    val id: String,
    val sessionId: String,
    val clientId: String,
    val appName: String?,
    val type: JobType,
    val status: JobStatus,
    val createdAt: Instant,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val output: String? = null,
    val errorCode: String? = null,
)

enum class JobType {
  GENERATE,
  TRANSCRIBE,
}

enum class JobStatus {
  QUEUED,
  RUNNING,
  COMPLETED,
  FAILED,
  CANCELLED;

  fun isCancellable() = this == QUEUED || this == RUNNING

  fun isTerminal() = this == COMPLETED || this == FAILED || this == CANCELLED
}
