/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.db

import androidx.room.TypeConverter
import app.altio.service.domain.model.ModelStatus

class ModelStatusConverter {
  @TypeConverter fun fromStatus(status: ModelStatus): String = status.name

  @TypeConverter fun toStatus(value: String): ModelStatus = ModelStatus.valueOf(value)
}
