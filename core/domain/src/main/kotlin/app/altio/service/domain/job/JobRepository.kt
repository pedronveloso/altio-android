/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.job

import java.time.Instant
import kotlinx.coroutines.flow.Flow

const val JOB_INTERRUPTED_ERROR_CODE = "JOB_INTERRUPTED"

interface JobRepository {
  fun getJob(id: String): Flow<Job?>

  fun observeAllJobs(): Flow<List<Job>>

  fun observeActiveJobs(): Flow<List<Job>>

  fun observeActiveJobCount(): Flow<Int>

  suspend fun createJob(job: Job): Job

  suspend fun updateStarted(id: String, startedAt: Instant)

  suspend fun complete(id: String, output: String, completedAt: Instant)

  suspend fun fail(id: String, errorCode: String, completedAt: Instant)

  suspend fun cancel(id: String, completedAt: Instant)

  /** Fails active jobs left behind by a previous process lifetime. */
  suspend fun failActiveJobsOnStartup(completedAt: Instant): Int

  /** Returns the number of QUEUED or RUNNING jobs belonging to [clientId]. */
  suspend fun countActiveByClient(clientId: String): Int
}
