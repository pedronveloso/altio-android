/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.token

data class ClientToken(
    val tokenHash: String,
    val clientId: String,
    val label: String,
    val createdAt: Long,
    val lastUsedAt: Long?,
    val isRevoked: Boolean,
)
