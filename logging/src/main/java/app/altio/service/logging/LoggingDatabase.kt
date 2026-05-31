/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.logging

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [LogEntryEntity::class], version = 1, exportSchema = false)
abstract class LoggingDatabase : RoomDatabase() {
  abstract fun logEntryDao(): LogEntryDao
}
