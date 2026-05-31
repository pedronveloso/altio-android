/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
  @Query("SELECT * FROM sessions WHERE id = :id") fun observe(id: String): Flow<SessionEntity?>

  @Query("SELECT * FROM sessions ORDER BY lastActiveAt DESC, createdAt DESC")
  fun observeAll(): Flow<List<SessionEntity>>

  @Query("SELECT COUNT(*) FROM sessions") fun observeCount(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: SessionEntity)

  @Query("DELETE FROM sessions WHERE id = :id") suspend fun delete(id: String)

  @Query("DELETE FROM sessions") suspend fun deleteAll()

  @Query("UPDATE sessions SET messageCount = messageCount + 1, lastActiveAt = :now WHERE id = :id")
  suspend fun incrementMessageCount(id: String, now: Instant)

  @Query("UPDATE sessions SET lastActiveAt = :now WHERE id = :id")
  suspend fun touchSession(id: String, now: Instant)

  @Query("UPDATE sessions SET messageCount = 0, lastActiveAt = :now WHERE id = :id")
  suspend fun resetSession(id: String, now: Instant)

  @Query("SELECT id FROM sessions WHERE lastActiveAt < :cutoff")
  suspend fun getStaleSessionIds(cutoff: Instant): List<String>

  @Query("SELECT COUNT(*) FROM sessions WHERE clientId = :clientId")
  suspend fun countByClient(clientId: String): Int
}
