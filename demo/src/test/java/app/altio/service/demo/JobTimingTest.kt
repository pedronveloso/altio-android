/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.demo

import app.altio.sdk.contract.job.JobStatus
import app.altio.sdk.contract.job.JobStatusResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JobTimingTest {

  @Test
  fun `formatJobDuration uses minute format below one hour`() {
    assertEquals("2:05", formatJobDuration(125_000L))
  }

  @Test
  fun `formatJobDuration uses hour format at one hour`() {
    assertEquals("1:01:05", formatJobDuration(3_665_000L))
  }

  @Test
  fun `formatJobDuration clamps negative durations`() {
    assertEquals("0:00", formatJobDuration(-1_000L))
  }

  @Test
  fun `formatJobTimingText reports queued elapsed time from creation`() {
    val job = job(status = JobStatus.QUEUED, createdAt = 1_000L)

    assertEquals("Queued for: 0:04", formatJobTimingText(job, nowMs = 5_000L))
  }

  @Test
  fun `formatJobTimingText reports running elapsed time from start`() {
    val job = job(status = JobStatus.RUNNING, createdAt = 1_000L, startedAt = 2_000L)

    assertEquals("Running for: 0:03", formatJobTimingText(job, nowMs = 5_000L))
  }

  @Test
  fun `formatJobTimingText reports completed duration from start to completion`() {
    val job =
        job(
            status = JobStatus.COMPLETED,
            createdAt = 1_000L,
            startedAt = 2_000L,
            completedAt = 7_000L,
        )

    assertEquals("Completed in: 0:05", formatJobTimingText(job, nowMs = 10_000L))
  }

  @Test
  fun `formatJobTimingText omits missing timestamps`() {
    assertNull(formatJobTimingText(job(status = JobStatus.QUEUED, createdAt = 0L), nowMs = 5_000L))
    assertNull(
        formatJobTimingText(
            job(status = JobStatus.COMPLETED, createdAt = 1_000L, completedAt = null),
            nowMs = 5_000L,
        )
    )
  }

  @Test
  fun `shouldUpdateJobClock is false when no jobs are tracked`() {
    assertEquals(false, shouldUpdateJobClock(emptyList(), emptyList()))
  }

  @Test
  fun `shouldUpdateJobClock is true while tracked job status is unknown`() {
    assertEquals(true, shouldUpdateJobClock(listOf("job-1"), emptyList()))
  }

  @Test
  fun `shouldUpdateJobClock is true while any tracked job is active`() {
    assertEquals(
        true,
        shouldUpdateJobClock(
            trackedJobIds = listOf("job-1", "job-2"),
            jobStatuses =
                listOf(
                    job(status = JobStatus.COMPLETED, createdAt = 1_000L),
                    job(id = "job-2", status = JobStatus.RUNNING, createdAt = 1_000L),
                ),
        ),
    )
  }

  @Test
  fun `shouldUpdateJobClock is false when all tracked jobs are terminal`() {
    assertEquals(
        false,
        shouldUpdateJobClock(
            trackedJobIds = listOf("job-1", "job-2"),
            jobStatuses =
                listOf(
                    job(status = JobStatus.COMPLETED, createdAt = 1_000L),
                    job(id = "job-2", status = JobStatus.FAILED, createdAt = 1_000L),
                ),
        ),
    )
  }

  private fun job(
      id: String = "job-1",
      status: JobStatus,
      createdAt: Long,
      startedAt: Long? = null,
      completedAt: Long? = null,
  ) =
      JobStatusResponse(
          jobId = id,
          sessionId = "session-1",
          status = status,
          createdAt = createdAt,
          startedAt = startedAt,
          completedAt = completedAt,
      )
}
