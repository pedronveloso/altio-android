/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.logging

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LogEntryDao {
  @Insert suspend fun insert(entry: LogEntryEntity)

  @Query("SELECT * FROM log_entries ORDER BY id ASC") suspend fun getAll(): List<LogEntryEntity>

  @Query(
      "DELETE FROM log_entries WHERE id NOT IN (SELECT id FROM log_entries ORDER BY id DESC LIMIT :limit)"
  )
  suspend fun trimToLimit(limit: Int)

  @Query("DELETE FROM log_entries") suspend fun clear()
}
