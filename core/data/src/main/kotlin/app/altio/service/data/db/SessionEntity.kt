/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val clientId: String,
    val appName: String?,
    val modelId: String,
    val systemPrompt: String?,
    val temperature: Float,
    val topK: Int,
    val topP: Float,
    val maxTokens: Int,
    val messageCount: Int,
    val createdAt: Instant,
    val lastActiveAt: Instant,
)
