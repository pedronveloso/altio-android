/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.runtime

data class RuntimeConfig(
    val accelerator: Accelerator = Accelerator.AUTO,
    val maxTokens: Int = 4000,
    val numThreads: Int = 4,
)

enum class Accelerator {
  AUTO,
  CPU,
  GPU,
  NPU,
}

data class GenerationConfig(
    val temperature: Float = 1.0f,
    val topK: Int = 64,
    val topP: Float = 0.95f,
    val maxTokens: Int = 4000,
)
