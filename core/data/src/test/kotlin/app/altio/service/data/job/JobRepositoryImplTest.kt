/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.job

import app.altio.service.data.db.JobDao
import app.altio.service.data.db.JobEntity
import app.altio.service.domain.job.JOB_INTERRUPTED_ERROR_CODE
import app.altio.service.domain.job.JobStatus
import app.altio.service.domain.job.JobType
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JobRepositoryImplTest {

  @Test
  fun `failActiveJobsOnStartup marks only queued and running jobs interrupted`() = runTest {
    val dao = FakeJobDao()
    val repository = JobRepositoryImpl(dao)
    val completedAt = Instant.parse("2026-05-27T10:15:30Z")

    dao.insert(makeJob("queued", JobStatus.QUEUED))
    dao.insert(makeJob("running", JobStatus.RUNNING))
    dao.insert(makeJob("completed", JobStatus.COMPLETED))
    dao.insert(makeJob("failed", JobStatus.FAILED))
    dao.insert(makeJob("cancelled", JobStatus.CANCELLED))

    val affected = repository.failActiveJobsOnStartup(completedAt)

    assertEquals(2, affected)
    assertInterrupted(dao.jobs.getValue("queued"), completedAt)
    assertInterrupted(dao.jobs.getValue("running"), completedAt)
    assertEquals(JobStatus.COMPLETED, dao.jobs.getValue("completed").status)
    assertEquals(JobStatus.FAILED, dao.jobs.getValue("failed").status)
    assertEquals(JobStatus.CANCELLED, dao.jobs.getValue("cancelled").status)
    assertNull(dao.jobs.getValue("completed").errorCode)
    assertEquals(emptyList<JobStatus>(), repository.observeActiveJobs().first().map { it.status })
  }

  private fun assertInterrupted(job: JobEntity, completedAt: Instant) {
    assertEquals(JobStatus.FAILED, job.status)
    assertEquals(completedAt, job.completedAt)
    assertEquals(JOB_INTERRUPTED_ERROR_CODE, job.errorCode)
  }
}

private class FakeJobDao : JobDao {
  val jobs = linkedMapOf<String, JobEntity>()

  override fun observe(id: String): Flow<JobEntity?> = flowOf(jobs[id])

  override fun observeAll(): Flow<List<JobEntity>> =
      flowOf(jobs.values.sortedByDescending { it.createdAt })

  override fun observeActive(): Flow<List<JobEntity>> =
      flowOf(
          jobs.values
              .filter { it.status == JobStatus.QUEUED || it.status == JobStatus.RUNNING }
              .sortedByDescending { it.createdAt }
      )

  override fun observeActiveCount(): Flow<Int> =
      flowOf(jobs.values.count { it.status == JobStatus.QUEUED || it.status == JobStatus.RUNNING })

  override suspend fun insert(entity: JobEntity) {
    jobs[entity.id] = entity
  }

  override suspend fun updateStarted(id: String, status: JobStatus, startedAt: Instant) {
    jobs[id] = jobs.getValue(id).copy(status = status, startedAt = startedAt)
  }

  override suspend fun complete(
      id: String,
      status: JobStatus,
      output: String,
      completedAt: Instant,
  ) {
    jobs[id] = jobs.getValue(id).copy(status = status, output = output, completedAt = completedAt)
  }

  override suspend fun fail(
      id: String,
      status: JobStatus,
      errorCode: String,
      completedAt: Instant,
  ) {
    jobs[id] =
        jobs.getValue(id).copy(status = status, errorCode = errorCode, completedAt = completedAt)
  }

  override suspend fun cancel(id: String, status: JobStatus, completedAt: Instant) {
    jobs[id] = jobs.getValue(id).copy(status = status, completedAt = completedAt)
  }

  override suspend fun failActiveJobsOnStartup(
      failedStatus: JobStatus,
      completedAt: Instant,
      errorCode: String,
  ): Int {
    val activeIds =
        jobs.values
            .filter { it.status == JobStatus.QUEUED || it.status == JobStatus.RUNNING }
            .map { it.id }
    activeIds.forEach { id ->
      jobs[id] =
          jobs
              .getValue(id)
              .copy(status = failedStatus, completedAt = completedAt, errorCode = errorCode)
    }
    return activeIds.size
  }

  override suspend fun countActiveByClient(clientId: String): Int =
      jobs.values.count {
        it.clientId == clientId && (it.status == JobStatus.QUEUED || it.status == JobStatus.RUNNING)
      }
}

private fun makeJob(id: String, status: JobStatus): JobEntity =
    JobEntity(
        id = id,
        sessionId = "ses-test",
        clientId = "client-test",
        appName = null,
        type = JobType.GENERATE,
        status = status,
        createdAt = Instant.parse("2026-05-27T10:00:00Z"),
        startedAt = null,
        completedAt = null,
        output = null,
        errorCode = null,
    )
