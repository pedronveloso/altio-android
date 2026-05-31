/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities =
        [
            SessionEntity::class,
            JobEntity::class,
        ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(ModelStatusConverter::class, InstantConverter::class, JobTypeConverter::class)
abstract class AiServiceDatabase : RoomDatabase() {
  abstract fun sessionDao(): SessionDao

  abstract fun jobDao(): JobDao
}
