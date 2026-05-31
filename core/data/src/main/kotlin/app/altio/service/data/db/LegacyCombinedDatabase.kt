/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Legacy combined database used before runtime and durable state were split into separate stores.
 * Read-only access is enough for one-time import.
 */
@Database(
    entities =
        [
            ModelEntity::class,
            SessionEntity::class,
            JobEntity::class,
            ClientTokenEntity::class,
        ],
    version = 6,
    exportSchema = false,
)
@TypeConverters(ModelStatusConverter::class, InstantConverter::class, JobTypeConverter::class)
abstract class LegacyCombinedDatabase : RoomDatabase() {
  abstract fun modelDao(): ModelDao

  abstract fun clientTokenDao(): ClientTokenDao
}
