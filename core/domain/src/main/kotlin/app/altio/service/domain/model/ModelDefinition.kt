/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.model

data class ModelDefinition(
    val id: String,
    val name: String,
    val description: String,
    val source: ModelSource,
    val version: String,
    val huggingfaceRepo: String,
    val sizeBytes: Long,
    val sha256: String,
    val capabilities: List<ModelCapability>,
    val minSdk: Int,
    val minDeviceMemoryGb: Int,
    val maxContextLength: Int,
    val runtime: String,
    val files: List<ModelFile>,
    val defaultConfig: ModelDefaultConfig,
)
