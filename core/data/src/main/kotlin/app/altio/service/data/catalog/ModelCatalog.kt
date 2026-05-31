/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.catalog

import app.altio.service.domain.model.ModelCapability
import app.altio.service.domain.model.ModelDefaultConfig
import app.altio.service.domain.model.ModelDefinition
import app.altio.service.domain.model.ModelFile
import app.altio.service.domain.model.ModelSource

/** Static catalog of available on-device models. Source of truth for download metadata. */
object ModelCatalog {
  const val GEMMA_4_E2B_IT_VERSION = "b4f4f4df93418ddb4aa7da8bf33b584602a5b9f8"
  const val GEMMA_4_E2B_IT_LEGACY_VERSION = "7fa1d78473894f7e736a21d920c3aa80f950c0db"
  const val GEMMA_4_E2B_IT_SHA256 =
      "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"

  const val QWEN_3_5_2B_INSTRUCT_VERSION = "2be7f4df93418ddb4aa7da8bf33b584602a5b9f8"
  const val QWEN_3_5_2B_INSTRUCT_SHA256 =
      "dae2948424603e26aa74c961f5494598d004b7af703ee954ec92c4e925bf1190"

  const val QWEN_3_5_0_8B_INSTRUCT_VERSION = "08be7f4df93418ddb4aa7da8bf33b584602a5b9f8"
  const val QWEN_3_5_0_8B_INSTRUCT_SHA256 =
      "92999fe4a9242c983e99892d6e57f368e8cd7a4534bc9a383a9551155b7f70a5"

  val gemma4E2bIt =
      ModelDefinition(
          id = "gemma-4-e2b-it",
          name = "Gemma 4 E2B IT",
          description =
              "Gemma 4 multimodal: text, vision, audio, thinking. 2.6 GB. Requires 8 GB device RAM.",
          source = ModelSource.DOWNLOADED,
          version = GEMMA_4_E2B_IT_VERSION,
          huggingfaceRepo = "litert-community/gemma-4-E2B-it-litert-lm",
          sizeBytes = 2_588_147_712L,
          sha256 = GEMMA_4_E2B_IT_SHA256,
          capabilities =
              listOf(
                  ModelCapability.TEXT,
                  ModelCapability.VISION,
                  ModelCapability.AUDIO,
                  ModelCapability.THINKING,
              ),
          minSdk = 31,
          minDeviceMemoryGb = 8,
          maxContextLength = 32_000,
          runtime = "litert-lm",
          files =
              listOf(
                  ModelFile(
                      name = "gemma-4-E2B-it.litertlm",
                      sizeBytes = 2_588_147_712L,
                      sha256 = GEMMA_4_E2B_IT_SHA256,
                  )
              ),
          defaultConfig =
              ModelDefaultConfig(
                  topK = 64,
                  topP = 0.95f,
                  temperature = 1.0f,
                  maxTokens = 4_000,
                  accelerators = listOf("gpu", "cpu"),
              ),
      )

  val qwen352bInstruct =
      ModelDefinition(
          id = "qwen-3.5-2b-instruct",
          name = "Qwen 3.5 2B Instruct",
          description =
              "Qwen 3.5 2B Instruct multimodal model. Optimized for text and vision tasks. 1.0 GB. Requires 6 GB device RAM.",
          source = ModelSource.DOWNLOADED,
          version = QWEN_3_5_2B_INSTRUCT_VERSION,
          huggingfaceRepo = "paulsp94/Qwen3.5-2B-LiteRT-LM",
          sizeBytes = 1_069_429_648L,
          sha256 = QWEN_3_5_2B_INSTRUCT_SHA256,
          capabilities =
              listOf(
                  ModelCapability.TEXT,
                  ModelCapability.VISION,
              ),
          minSdk = 31,
          minDeviceMemoryGb = 6,
          maxContextLength = 32_000,
          runtime = "litert-lm",
          files =
              listOf(
                  ModelFile(
                      name = "qwen35_2b_q4.litertlm",
                      sizeBytes = 1_069_429_648L,
                      sha256 = QWEN_3_5_2B_INSTRUCT_SHA256,
                  )
              ),
          defaultConfig =
              ModelDefaultConfig(
                  topK = 40,
                  topP = 0.8f,
                  temperature = 0.7f,
                  maxTokens = 4_000,
                  accelerators = listOf("gpu", "cpu"),
              ),
      )

  val qwen3508bInstruct =
      ModelDefinition(
          id = "qwen-3.5-0.8b-instruct",
          name = "Qwen 3.5 0.8B Instruct",
          description =
              "Qwen 3.5 0.8B Instruct multimodal model. Extremely lightweight text and vision model. 1.1 GB. Requires 4 GB device RAM.",
          source = ModelSource.DOWNLOADED,
          version = QWEN_3_5_0_8B_INSTRUCT_VERSION,
          huggingfaceRepo = "GabrieleConte/Qwen3.5-0.8B-LiteRT",
          sizeBytes = 1_159_757_824L,
          sha256 = QWEN_3_5_0_8B_INSTRUCT_SHA256,
          capabilities =
              listOf(
                  ModelCapability.TEXT,
                  ModelCapability.VISION,
              ),
          minSdk = 31,
          minDeviceMemoryGb = 4,
          maxContextLength = 32_000,
          runtime = "litert-lm",
          files =
              listOf(
                  ModelFile(
                      name = "qwen35_mm_q8_ekv2048.litertlm",
                      sizeBytes = 1_159_757_824L,
                      sha256 = QWEN_3_5_0_8B_INSTRUCT_SHA256,
                  )
              ),
          defaultConfig =
              ModelDefaultConfig(
                  topK = 40,
                  topP = 0.8f,
                  temperature = 0.7f,
                  maxTokens = 4_000,
                  accelerators = listOf("gpu", "cpu"),
              ),
      )

  val demoModel =
      ModelDefinition(
          id = "demo-model",
          name = "Demo Model",
          description =
              "Built-in mock model for local service testing. No download required. Randomized text and audio responses.",
          source = ModelSource.BUILT_IN,
          version = "1",
          huggingfaceRepo = "",
          sizeBytes = 0L,
          sha256 = "",
          capabilities =
              listOf(
                  ModelCapability.TEXT,
                  ModelCapability.AUDIO,
                  ModelCapability.THINKING,
              ),
          minSdk = 31,
          minDeviceMemoryGb = 0,
          maxContextLength = 8_192,
          runtime = "demo",
          files = emptyList(),
          defaultConfig =
              ModelDefaultConfig(
                  topK = 40,
                  topP = 0.9f,
                  temperature = 0.8f,
                  maxTokens = 1024,
                  accelerators = listOf("cpu"),
              ),
      )

  val all: List<ModelDefinition> =
      listOf(demoModel, gemma4E2bIt, qwen352bInstruct, qwen3508bInstruct)

  fun find(id: String): ModelDefinition? = all.firstOrNull { it.id == id }
}
