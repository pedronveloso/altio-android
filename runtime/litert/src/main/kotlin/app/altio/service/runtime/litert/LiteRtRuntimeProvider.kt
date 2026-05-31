/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.runtime.litert

import app.altio.service.domain.model.Model
import app.altio.service.domain.model.ModelCapability
import app.altio.service.domain.runtime.Accelerator
import app.altio.service.domain.runtime.RuntimeConfig
import app.altio.service.domain.runtime.RuntimeEngine
import app.altio.service.domain.runtime.RuntimeProvider
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class LiteRtRuntimeProvider(
    /** Absolute path to the directory used for KV-cache and model compilation artefacts. */
    private val cacheDir: String,
) : RuntimeProvider {

  override fun supports(model: Model): Boolean = model.definition.runtime == "litert-lm"

  override suspend fun load(model: Model, config: RuntimeConfig): RuntimeEngine =
      withContext(Dispatchers.IO) {
        val modelPath =
            requireNotNull(model.filePath) {
              "Model ${model.definition.id} requires a downloaded file before it can be loaded"
            }
        val preferredBackend = config.accelerator.toBackend()
        val enableAudio = model.definition.capabilities.contains(ModelCapability.AUDIO)
        val engineResult = tryInitEngine(modelPath, preferredBackend, config.maxTokens, enableAudio)
        val engine =
            engineResult.getOrElse { preferredError ->
              if (config.accelerator == Accelerator.AUTO) {
                // GPU init failed under AUTO — fall back to CPU
                tryInitEngine(modelPath, Backend.CPU(), config.maxTokens, enableAudio).getOrElse {
                    cpuError ->
                  val fallbackError =
                      IllegalStateException(
                          "Engine initialization failed on both GPU and CPU",
                          cpuError,
                      )
                  fallbackError.addSuppressed(preferredError)
                  throw fallbackError
                }
              } else {
                throw IllegalStateException(
                    "Engine initialization failed for backend $preferredBackend",
                    preferredError,
                )
              }
            }
        LiteRtRuntimeEngine(engine)
      }

  private fun tryInitEngine(
      modelPath: String,
      backend: Backend,
      maxTokens: Int,
      enableAudio: Boolean,
  ): Result<Engine> =
      runCatching {
            val engineConfig =
                EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    maxNumTokens = maxTokens,
                    cacheDir = cacheDir,
                    audioBackend = if (enableAudio) Backend.CPU() else null,
                )
            Engine(engineConfig).also { it.initialize() }
          }
          .onFailure { e -> Timber.e(e, "Engine init failed for backend %s", backend) }

  private fun Accelerator.toBackend(): Backend =
      when (this) {
        Accelerator.CPU -> Backend.CPU()
        Accelerator.GPU,
        Accelerator.AUTO -> Backend.GPU()
        Accelerator.NPU -> Backend.CPU() // NPU requires nativeLibraryDir; fall back to CPU
      }
}
