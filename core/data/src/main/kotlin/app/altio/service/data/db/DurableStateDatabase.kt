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
            ModelEntity::class,
            ClientTokenEntity::class,
        ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(ModelStatusConverter::class)
abstract class DurableStateDatabase : RoomDatabase() {
  abstract fun modelDao(): ModelDao

  abstract fun clientTokenDao(): ClientTokenDao
}
