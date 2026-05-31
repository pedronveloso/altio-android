/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.state

import app.altio.service.ModelManagementSummary
import app.altio.service.data.runtime.RuntimeEngineHolder
import app.altio.service.deriveModelManagementSummary
import app.altio.service.domain.model.DownloadProgress
import app.altio.service.domain.model.Model
import app.altio.service.domain.model.ModelRepository
import app.altio.service.domain.model.ModelSource
import app.altio.service.domain.settings.AppSettings
import app.altio.service.domain.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class ModelState(
    val settings: AppSettings = AppSettings(),
    val models: List<Model> = emptyList(),
    val modelsLoaded: Boolean = false,
    val downloadProgressByModelId: Map<String, DownloadProgress?> = emptyMap(),
    val activeModel: Model? = null,
    val activeDownloadProgress: DownloadProgress? = null,
    val loadedModelId: String? = null,
    val modelManagementSummary: ModelManagementSummary =
        ModelManagementSummary(actionNeeded = false),
)

interface ModelStateStore {
  val state: StateFlow<ModelState>
}

@OptIn(ExperimentalCoroutinesApi::class)
class ModelStateStoreImpl(
    settingsRepository: SettingsRepository,
    modelRepository: ModelRepository,
    engineHolder: RuntimeEngineHolder,
    scope: CoroutineScope,
    started: SharingStarted = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
) : ModelStateStore {
  private val modelsFlow: Flow<List<Model>?> = modelRepository.modelsFlow()

  private val downloadProgressByModelIdFlow: Flow<Map<String, DownloadProgress?>> =
      modelsFlow.downloadProgressByModelIdFlow(modelRepository::getDownloadProgress)

  override val state: StateFlow<ModelState> =
      buildModelStateFlow(
              settingsFlow = settingsRepository.settings,
              modelsFlow = modelsFlow,
              downloadProgressByModelIdFlow = downloadProgressByModelIdFlow,
              loadedModelIdFlow = engineHolder.loadedModelIdFlow,
          )
          .stateIn(
              scope = scope,
              started = started,
              initialValue = ModelState(),
          )
}

internal fun ModelRepository.modelsFlow(): Flow<List<Model>?> =
    getAvailableModels().map<List<Model>, List<Model>?> { it }.onStart { emit(null) }

@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<List<Model>?>.downloadProgressByModelIdFlow(
    observeProgress: (String) -> Flow<DownloadProgress>,
): Flow<Map<String, DownloadProgress?>> =
    map { models ->
          models
              .orEmpty()
              .filter { it.definition.source == ModelSource.DOWNLOADED }
              .map { it.definition.id }
        }
        .distinctUntilChanged()
        .flatMapLatest { downloadableModelIds ->
          if (downloadableModelIds.isEmpty()) {
            flowOf(emptyMap())
          } else {
            combine(downloadableModelIds.map(observeProgress)) { progressItems ->
              downloadableModelIds.zip(progressItems.toList()).toMap()
            }
          }
        }
        .onStart { emit(emptyMap()) }

internal fun buildModelStateFlow(
    settingsFlow: Flow<AppSettings>,
    modelsFlow: Flow<List<Model>?>,
    downloadProgressByModelIdFlow: Flow<Map<String, DownloadProgress?>>,
    loadedModelIdFlow: Flow<String?>,
): Flow<ModelState> =
    combine(
        settingsFlow,
        modelsFlow,
        downloadProgressByModelIdFlow,
        loadedModelIdFlow,
    ) { settings, modelsOrNull, downloadProgressByModelId, loadedModelId ->
      val models = modelsOrNull.orEmpty()
      val activeModel = models.firstOrNull { it.definition.id == settings.activeModelId }
      val activeDownloadProgress = activeModel?.let { downloadProgressByModelId[it.definition.id] }
      ModelState(
          settings = settings,
          models = models,
          modelsLoaded = modelsOrNull != null,
          downloadProgressByModelId = downloadProgressByModelId,
          activeModel = activeModel,
          activeDownloadProgress = activeDownloadProgress,
          loadedModelId = loadedModelId,
          modelManagementSummary =
              deriveModelManagementSummary(
                  activeModelId = settings.activeModelId,
                  models = models,
                  downloadProgressByModelId = downloadProgressByModelId,
              ),
      )
    }

private const val STOP_TIMEOUT_MILLIS = 5_000L
