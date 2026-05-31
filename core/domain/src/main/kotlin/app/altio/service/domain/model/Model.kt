/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.model

data class Model(
    val definition: ModelDefinition,
    val status: ModelStatus,
    val filePath: String?,
    val downloadedAt: Long?,
)
