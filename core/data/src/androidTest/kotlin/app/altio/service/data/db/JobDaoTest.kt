/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.altio.service.domain.job.JOB_INTERRUPTED_ERROR_CODE
import app.altio.service.domain.job.JobStatus
import app.altio.service.domain.job.JobType
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JobDaoTest {

  private lateinit var db: AiServiceDatabase
  private lateinit var dao: JobDao

  @Before
  fun setUp() {
    db =
        Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AiServiceDatabase::class.java,
            )
            .allowMainThreadQueries()
            .build()
    dao = db.jobDao()
  }

  @After
  fun tearDown() {
    db.close()
  }

  // ─── insert / observe ────────────────────────────────────────────────────

  @Test
  fun insertAndObserve_returnsJob() = runTest {
    dao.insert(makeJob("job-1", status = JobStatus.QUEUED))
    val result = dao.observe("job-1").first()
    assertNotNull(result)
    assertEquals(JobStatus.QUEUED, result?.status)
  }

  @Test
  fun observe_returnsNull_forMissingId() = runTest {
    assertNull(dao.observe("nonexistent").first())
  }

  // ─── updateStarted ────────────────────────────────────────────────────────

  @Test
  fun updateStarted_setsRunningStatusAndTimestamp() = runTest {
    dao.insert(makeJob("job-start", status = JobStatus.QUEUED))
    val startTime = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    dao.updateStarted("job-start", JobStatus.RUNNING, startTime)

    val result = dao.observe("job-start").first()
    assertEquals(JobStatus.RUNNING, result?.status)
    assertEquals(startTime, result?.startedAt)
  }

  // ─── complete ─────────────────────────────────────────────────────────────

  @Test
  fun complete_setsCompletedStatusAndOutput() = runTest {
    dao.insert(makeJob("job-done", status = JobStatus.RUNNING))
    val doneAt = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    dao.complete("job-done", JobStatus.COMPLETED, "the answer", doneAt)

    val result = dao.observe("job-done").first()
    assertEquals(JobStatus.COMPLETED, result?.status)
    assertEquals("the answer", result?.output)
    assertEquals(doneAt, result?.completedAt)
  }

  // ─── fail ─────────────────────────────────────────────────────────────────

  @Test
  fun fail_setsFailedStatusAndErrorCode() = runTest {
    dao.insert(makeJob("job-fail", status = JobStatus.RUNNING))
    val failedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    dao.fail("job-fail", JobStatus.FAILED, "INFERENCE_FAILED", failedAt)

    val result = dao.observe("job-fail").first()
    assertEquals(JobStatus.FAILED, result?.status)
    assertEquals("INFERENCE_FAILED", result?.errorCode)
    assertEquals(failedAt, result?.completedAt)
  }

  // ─── cancel ───────────────────────────────────────────────────────────────

  @Test
  fun cancel_setsCancelledStatus() = runTest {
    dao.insert(makeJob("job-cancel", status = JobStatus.RUNNING))
    val cancelledAt = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    dao.cancel("job-cancel", JobStatus.CANCELLED, cancelledAt)

    val result = dao.observe("job-cancel").first()
    assertEquals(JobStatus.CANCELLED, result?.status)
    assertEquals(cancelledAt, result?.completedAt)
  }

  // ─── countActiveByClient ──────────────────────────────────────────────────

  @Test
  fun countActiveByClient_countsQueuedAndRunning() = runTest {
    dao.insert(makeJob("j-q", clientId = "c1", status = JobStatus.QUEUED))
    dao.insert(makeJob("j-r", clientId = "c1", status = JobStatus.RUNNING))
    dao.insert(makeJob("j-d", clientId = "c1", status = JobStatus.COMPLETED))
    dao.insert(makeJob("j-f", clientId = "c1", status = JobStatus.FAILED))

    assertEquals(2, dao.countActiveByClient("c1"))
  }

  @Test
  fun countActiveByClient_doesNotCountOtherClients() = runTest {
    dao.insert(makeJob("j-other", clientId = "c2", status = JobStatus.RUNNING))

    assertEquals(0, dao.countActiveByClient("c1"))
  }

  @Test
  fun countActiveByClient_returnsZeroWhenEmpty() = runTest {
    assertEquals(0, dao.countActiveByClient("c1"))
  }

  @Test
  fun failActiveJobsOnStartup_failsOnlyQueuedAndRunningJobs() = runTest {
    val completedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    dao.insert(makeJob("j-q", status = JobStatus.QUEUED))
    dao.insert(makeJob("j-r", status = JobStatus.RUNNING))
    dao.insert(makeJob("j-c", status = JobStatus.COMPLETED))
    dao.insert(makeJob("j-f", status = JobStatus.FAILED))
    dao.insert(makeJob("j-x", status = JobStatus.CANCELLED))

    val affected =
        dao.failActiveJobsOnStartup(
            failedStatus = JobStatus.FAILED,
            completedAt = completedAt,
            errorCode = JOB_INTERRUPTED_ERROR_CODE,
        )

    assertEquals(2, affected)
    assertEquals(JobStatus.FAILED, dao.observe("j-q").first()?.status)
    assertEquals(JobStatus.FAILED, dao.observe("j-r").first()?.status)
    assertEquals(completedAt, dao.observe("j-q").first()?.completedAt)
    assertEquals(completedAt, dao.observe("j-r").first()?.completedAt)
    assertEquals(JOB_INTERRUPTED_ERROR_CODE, dao.observe("j-q").first()?.errorCode)
    assertEquals(JOB_INTERRUPTED_ERROR_CODE, dao.observe("j-r").first()?.errorCode)
    assertEquals(JobStatus.COMPLETED, dao.observe("j-c").first()?.status)
    assertEquals(JobStatus.FAILED, dao.observe("j-f").first()?.status)
    assertEquals(JobStatus.CANCELLED, dao.observe("j-x").first()?.status)
    assertEquals(emptyList<JobEntity>(), dao.observeActive().first())
  }
}

private fun makeJob(
    id: String,
    clientId: String = "client-test",
    status: JobStatus = JobStatus.QUEUED,
) =
    JobEntity(
        id = id,
        sessionId = "ses-test",
        clientId = clientId,
        appName = null,
        type = JobType.GENERATE,
        status = status,
        createdAt = Instant.now(),
        startedAt = null,
        completedAt = null,
        output = null,
        errorCode = null,
    )
