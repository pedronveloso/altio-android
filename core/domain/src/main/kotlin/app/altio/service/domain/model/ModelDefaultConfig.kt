/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.model

data class ModelDefaultConfig(
    val topK: Int,
    val topP: Float,
    val temperature: Float,
    val maxTokens: Int,
    val accelerators: List<String>,
)
