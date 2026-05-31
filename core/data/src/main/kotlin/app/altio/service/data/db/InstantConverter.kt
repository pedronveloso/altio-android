/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.db

import androidx.room.TypeConverter
import java.time.Instant

class InstantConverter {
  @TypeConverter fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

  @TypeConverter fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }
}
