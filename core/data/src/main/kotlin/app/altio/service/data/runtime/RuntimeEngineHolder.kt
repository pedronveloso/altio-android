/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.runtime

import app.altio.service.domain.model.Model
import app.altio.service.domain.runtime.RuntimeConfig
import app.altio.service.domain.runtime.RuntimeEngine
import app.altio.service.domain.runtime.RuntimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Holds the single currently-loaded [RuntimeEngine]. Only one model is loaded at a time; loading a
 * different model closes the previous engine automatically.
 */
class RuntimeEngineHolder(private val provider: RuntimeProvider) {
  private val mutex = Mutex()
  private var currentModelId: String? = null
  private var currentConfig: RuntimeConfig? = null
  private var engine: RuntimeEngine? = null
  private var onEngineWillReset: (suspend () -> Unit)? = null

  private val _loadedModelId = MutableStateFlow<String?>(null)
  val loadedModelIdFlow: StateFlow<String?> = _loadedModelId.asStateFlow()

  fun registerOnEngineWillReset(listener: suspend () -> Unit) {
    onEngineWillReset = listener
  }

  suspend fun load(model: Model, config: RuntimeConfig): RuntimeEngine =
      mutex.withLock {
        val existing = engine
        if (existing != null && currentModelId == model.definition.id && currentConfig == config) {
          Timber.d("Model %s already loaded — reusing", model.definition.id)
          return@withLock existing
        }
        if (existing != null) {
          onEngineWillReset?.invoke()
        }
        existing?.close()
        engine = null
        currentModelId = null
        currentConfig = null
        _loadedModelId.value = null
        Timber.i("Loading model %s", model.definition.id)
        val new = provider.load(model, config)
        engine = new
        currentModelId = model.definition.id
        currentConfig = config
        _loadedModelId.value = model.definition.id
        new
      }

  suspend fun unload() =
      mutex.withLock {
        if (engine != null) {
          onEngineWillReset?.invoke()
        }
        Timber.i("Unloading model %s", currentModelId)
        engine?.close()
        engine = null
        currentModelId = null
        currentConfig = null
        _loadedModelId.value = null
      }

  val loadedModelId: String?
    get() = currentModelId
}
