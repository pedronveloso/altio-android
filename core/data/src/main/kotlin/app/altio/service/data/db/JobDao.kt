/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.altio.service.domain.job.JobStatus
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
  @Query("SELECT * FROM jobs WHERE id = :id") fun observe(id: String): Flow<JobEntity?>

  @Query("SELECT * FROM jobs ORDER BY createdAt DESC") fun observeAll(): Flow<List<JobEntity>>

  @Query("SELECT * FROM jobs WHERE status IN ('QUEUED', 'RUNNING') ORDER BY createdAt DESC")
  fun observeActive(): Flow<List<JobEntity>>

  @Query("SELECT COUNT(*) FROM jobs WHERE status IN ('QUEUED', 'RUNNING')")
  fun observeActiveCount(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: JobEntity)

  @Query("UPDATE jobs SET status = :status, startedAt = :startedAt WHERE id = :id")
  suspend fun updateStarted(id: String, status: JobStatus, startedAt: Instant)

  @Query(
      "UPDATE jobs SET status = :status, output = :output, completedAt = :completedAt WHERE id = :id"
  )
  suspend fun complete(id: String, status: JobStatus, output: String, completedAt: Instant)

  @Query(
      "UPDATE jobs SET status = :status, errorCode = :errorCode, completedAt = :completedAt WHERE id = :id"
  )
  suspend fun fail(id: String, status: JobStatus, errorCode: String, completedAt: Instant)

  @Query("UPDATE jobs SET status = :status, completedAt = :completedAt WHERE id = :id")
  suspend fun cancel(id: String, status: JobStatus, completedAt: Instant)

  @Query(
      """
      UPDATE jobs
      SET status = :failedStatus, completedAt = :completedAt, errorCode = :errorCode
      WHERE status IN ('QUEUED', 'RUNNING')
      """
  )
  suspend fun failActiveJobsOnStartup(
      failedStatus: JobStatus,
      completedAt: Instant,
      errorCode: String,
  ): Int

  @Query("SELECT COUNT(*) FROM jobs WHERE clientId = :clientId AND status IN ('QUEUED', 'RUNNING')")
  suspend fun countActiveByClient(clientId: String): Int
}
