/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.altio.service.domain.model.ModelStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
  @Query("SELECT * FROM models") fun observeAll(): Flow<List<ModelEntity>>

  @Query("SELECT * FROM models WHERE id = :id") fun observe(id: String): Flow<ModelEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: ModelEntity)

  @Query("SELECT * FROM models") suspend fun getAll(): List<ModelEntity>

  @Query("SELECT COUNT(*) FROM models") suspend fun countRows(): Int

  @Query("UPDATE models SET status = :status WHERE id = :id")
  suspend fun updateStatus(id: String, status: ModelStatus)

  @Query(
      "UPDATE models SET status = :status, filePath = :filePath, downloadedAt = :downloadedAt WHERE id = :id"
  )
  suspend fun updateReady(id: String, status: ModelStatus, filePath: String, downloadedAt: Long)

  @Query("UPDATE models SET status = :status, filePath = NULL, downloadedAt = NULL WHERE id = :id")
  suspend fun clearDownloadedState(id: String, status: ModelStatus)

  @Query("DELETE FROM models WHERE id = :id") suspend fun delete(id: String)
}
