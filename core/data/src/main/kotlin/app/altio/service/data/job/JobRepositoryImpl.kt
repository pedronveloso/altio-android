/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.job

import app.altio.service.data.db.JobDao
import app.altio.service.data.db.JobEntity
import app.altio.service.domain.job.JOB_INTERRUPTED_ERROR_CODE
import app.altio.service.domain.job.Job
import app.altio.service.domain.job.JobRepository
import app.altio.service.domain.job.JobStatus
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class JobRepositoryImpl(private val dao: JobDao) : JobRepository {

  override fun getJob(id: String): Flow<Job?> = dao.observe(id).map { it?.toDomain() }

  override fun observeAllJobs(): Flow<List<Job>> =
      dao.observeAll().map { jobs -> jobs.map { it.toDomain() } }

  override fun observeActiveJobs(): Flow<List<Job>> =
      dao.observeActive().map { jobs -> jobs.map { it.toDomain() } }

  override fun observeActiveJobCount(): Flow<Int> = dao.observeActiveCount()

  override suspend fun createJob(job: Job): Job {
    dao.insert(job.toEntity())
    return job
  }

  override suspend fun updateStarted(id: String, startedAt: Instant) {
    dao.updateStarted(id, JobStatus.RUNNING, startedAt)
  }

  override suspend fun complete(id: String, output: String, completedAt: Instant) {
    dao.complete(id, JobStatus.COMPLETED, output, completedAt)
  }

  override suspend fun fail(id: String, errorCode: String, completedAt: Instant) {
    dao.fail(id, JobStatus.FAILED, errorCode, completedAt)
  }

  override suspend fun cancel(id: String, completedAt: Instant) {
    dao.cancel(id, JobStatus.CANCELLED, completedAt)
  }

  override suspend fun failActiveJobsOnStartup(completedAt: Instant): Int =
      dao.failActiveJobsOnStartup(
          failedStatus = JobStatus.FAILED,
          completedAt = completedAt,
          errorCode = JOB_INTERRUPTED_ERROR_CODE,
      )

  override suspend fun countActiveByClient(clientId: String): Int =
      dao.countActiveByClient(clientId)

  private fun JobEntity.toDomain() =
      Job(
          id = id,
          sessionId = sessionId,
          clientId = clientId,
          appName = appName,
          type = type,
          status = status,
          createdAt = createdAt,
          startedAt = startedAt,
          completedAt = completedAt,
          output = output,
          errorCode = errorCode,
      )

  private fun Job.toEntity() =
      JobEntity(
          id = id,
          sessionId = sessionId,
          clientId = clientId,
          appName = appName,
          type = type,
          status = status,
          createdAt = createdAt,
          startedAt = startedAt,
          completedAt = completedAt,
          output = output,
          errorCode = errorCode,
      )
}
