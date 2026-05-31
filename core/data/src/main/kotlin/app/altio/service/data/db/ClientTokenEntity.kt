/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists client bearer tokens. The raw token is never stored; only its SHA-256 hex digest is kept
 * so that a DB breach does not expose valid credentials.
 */
@Entity(tableName = "client_tokens")
data class ClientTokenEntity(
    @PrimaryKey val tokenHash: String,
    val clientId: String,
    val label: String,
    val createdAt: Long,
    val lastUsedAt: Long?,
    val isRevoked: Boolean,
)
