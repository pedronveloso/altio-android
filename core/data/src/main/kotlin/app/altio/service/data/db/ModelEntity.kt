/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.altio.service.domain.model.ModelStatus

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val sizeBytes: Long,
    val status: ModelStatus,
    val downloadedAt: Long?,
    val filePath: String?,
)
