/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.model

data class ModelFile(
    val name: String,
    val sizeBytes: Long,
    val sha256: String,
)
