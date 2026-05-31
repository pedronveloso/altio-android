/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.state

import app.altio.service.domain.model.DownloadProgress
import app.altio.service.domain.model.DownloadStatus
import app.altio.service.domain.model.Model
import app.altio.service.domain.model.ModelCapability
import app.altio.service.domain.model.ModelDefaultConfig
import app.altio.service.domain.model.ModelDefinition
import app.altio.service.domain.model.ModelFile
import app.altio.service.domain.model.ModelSource
import app.altio.service.domain.model.ModelStatus
import app.altio.service.domain.settings.AppSettings
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelStateStoreTest {

  @Test
  fun `emits modelsLoaded false before first model emission`() = runTest {
    val settingsFlow = MutableStateFlow(AppSettings())
    val modelsFlow = MutableStateFlow<List<Model>?>(null)
    val progressRepository = FakeProgressRepository()
    val latest =
        collectLatestModelState(
            settingsFlow = settingsFlow,
            modelsFlow = modelsFlow,
            progressRepository = progressRepository,
        )

    advanceUntilIdle()

    assertFalse(latest.value.modelsLoaded)
    assertEquals(emptyList<Model>(), latest.value.models)
    assertNull(latest.value.activeModel)
    latest.job.cancel()
  }

  @Test
  fun `aggregates per model progress into expected map`() = runTest {
    val settingsFlow = MutableStateFlow(AppSettings())
    val modelsFlow = MutableStateFlow<List<Model>?>(null)
    val progressRepository = FakeProgressRepository()
    val latest =
        collectLatestModelState(
            settingsFlow = settingsFlow,
            modelsFlow = modelsFlow,
            progressRepository = progressRepository,
        )
    val downloadableA = downloadableModel(id = "a", status = ModelStatus.READY)
    val downloadableB = downloadableModel(id = "b", status = ModelStatus.DOWNLOADING)
    val builtIn = builtInModel(id = "demo-model")

    modelsFlow.value = listOf(downloadableA, downloadableB, builtIn)
    progressRepository.emit(
        progress(modelId = "a", status = DownloadStatus.SUCCESS, bytesDownloaded = 100)
    )
    progressRepository.emit(
        progress(modelId = "b", status = DownloadStatus.DOWNLOADING, bytesDownloaded = 50)
    )
    advanceUntilIdle()

    assertEquals(
        mapOf(
            "a" to progress(modelId = "a", status = DownloadStatus.SUCCESS, bytesDownloaded = 100),
            "b" to
                progress(modelId = "b", status = DownloadStatus.DOWNLOADING, bytesDownloaded = 50),
        ),
        latest.value.downloadProgressByModelId,
    )
    latest.job.cancel()
  }

  @Test
  fun `updates activeModel when settings activeModelId changes`() = runTest {
    val settingsFlow = MutableStateFlow(AppSettings())
    val modelsFlow = MutableStateFlow<List<Model>?>(null)
    val progressRepository = FakeProgressRepository()
    val latest =
        collectLatestModelState(
            settingsFlow = settingsFlow,
            modelsFlow = modelsFlow,
            progressRepository = progressRepository,
        )
    val alpha = downloadableModel(id = "alpha", status = ModelStatus.READY)
    val beta = downloadableModel(id = "beta", status = ModelStatus.READY)

    modelsFlow.value = listOf(alpha, beta)
    settingsFlow.value = AppSettings(activeModelId = "alpha")
    advanceUntilIdle()
    assertEquals("alpha", latest.value.activeModel?.definition?.id)

    settingsFlow.value = AppSettings(activeModelId = "beta")
    advanceUntilIdle()
    assertEquals("beta", latest.value.activeModel?.definition?.id)
    latest.job.cancel()
  }

  @Test
  fun `recomputes modelManagementSummary for paused failed downloading and not downloaded cases`() =
      runTest {
        val settingsFlow = MutableStateFlow(AppSettings(activeModelId = "active"))
        val modelsFlow = MutableStateFlow<List<Model>?>(null)
        val progressRepository = FakeProgressRepository()
        val latest =
            collectLatestModelState(
                settingsFlow = settingsFlow,
                modelsFlow = modelsFlow,
                progressRepository = progressRepository,
            )
        val activeReady = downloadableModel(id = "active", status = ModelStatus.READY)
        val activeNotDownloaded =
            downloadableModel(id = "active", status = ModelStatus.NOT_DOWNLOADED)

        modelsFlow.value = listOf(activeReady)
        progressRepository.emit(progress("active", DownloadStatus.PAUSED))
        advanceUntilIdle()
        assertEquals("Download paused", latest.value.modelManagementSummary.statusMessage)

        progressRepository.emit(progress("active", DownloadStatus.FAILED))
        advanceUntilIdle()
        assertEquals("Download failed", latest.value.modelManagementSummary.statusMessage)

        progressRepository.emit(
            progress("active", DownloadStatus.DOWNLOADING, bytesDownloaded = 25)
        )
        advanceUntilIdle()
        assertEquals("Download in progress", latest.value.modelManagementSummary.statusMessage)

        modelsFlow.value = listOf(activeNotDownloaded)
        advanceUntilIdle()
        assertEquals("Model download required", latest.value.modelManagementSummary.statusMessage)
        latest.job.cancel()
      }

  @Test
  fun `preserves stable aggregation when model statuses change but downloadable ids do not`() =
      runTest {
        val settingsFlow = MutableStateFlow(AppSettings())
        val modelsFlow = MutableStateFlow<List<Model>?>(null)
        val progressRepository = FakeProgressRepository()
        val latest =
            collectLatestModelState(
                settingsFlow = settingsFlow,
                modelsFlow = modelsFlow,
                progressRepository = progressRepository,
            )
        val firstModels =
            listOf(
                downloadableModel(id = "alpha", status = ModelStatus.READY),
                downloadableModel(id = "beta", status = ModelStatus.DOWNLOADING),
            )
        val secondModels =
            listOf(
                downloadableModel(id = "alpha", status = ModelStatus.LOADED),
                downloadableModel(id = "beta", status = ModelStatus.READY),
            )

        modelsFlow.value = firstModels
        advanceUntilIdle()
        assertEquals(1, progressRepository.requestCount("alpha"))
        assertEquals(1, progressRepository.requestCount("beta"))

        modelsFlow.value = secondModels
        advanceUntilIdle()
        assertEquals(1, progressRepository.requestCount("alpha"))
        assertEquals(1, progressRepository.requestCount("beta"))
        latest.job.cancel()
      }
}

private data class LatestModelState(
    val valueFlow: MutableStateFlow<ModelState>,
    val job: Job,
) {
  val value: ModelState
    get() = valueFlow.value
}

private fun kotlinx.coroutines.CoroutineScope.collectLatestModelState(
    settingsFlow: Flow<AppSettings>,
    modelsFlow: Flow<List<Model>?>,
    progressRepository: FakeProgressRepository,
): LatestModelState {
  val latest = MutableStateFlow(ModelState())
  val job =
      launch(EmptyCoroutineContext) {
        buildModelStateFlow(
                settingsFlow = settingsFlow,
                modelsFlow = modelsFlow,
                downloadProgressByModelIdFlow =
                    modelsFlow.downloadProgressByModelIdFlow(progressRepository::observe),
                loadedModelIdFlow = MutableStateFlow(null),
            )
            .collect { latest.value = it }
      }
  return LatestModelState(valueFlow = latest, job = job)
}

private class FakeProgressRepository {
  private val progressFlows = mutableMapOf<String, MutableStateFlow<DownloadProgress>>()
  private val requests = mutableMapOf<String, Int>()

  fun observe(id: String): Flow<DownloadProgress> {
    requests[id] = (requests[id] ?: 0) + 1
    return progressFlows.getOrPut(id) { MutableStateFlow(progress(id, DownloadStatus.CANCELLED)) }
  }

  fun emit(progress: DownloadProgress) {
    progressFlows
        .getOrPut(progress.modelId) {
          MutableStateFlow(progress(progress.modelId, DownloadStatus.CANCELLED))
        }
        .value = progress
  }

  fun requestCount(id: String): Int = requests[id] ?: 0
}

private fun progress(
    modelId: String,
    status: DownloadStatus,
    bytesDownloaded: Long = 0L,
): DownloadProgress =
    DownloadProgress(
        modelId = modelId,
        bytesDownloaded = bytesDownloaded,
        totalBytes = 100L,
        bytesPerSec = 10L,
        etaMs = 1_000L,
        status = status,
    )

private fun downloadableModel(id: String, status: ModelStatus): Model =
    Model(
        definition =
            ModelDefinition(
                id = id,
                name = id,
                description = "test",
                source = ModelSource.DOWNLOADED,
                version = "1",
                huggingfaceRepo = "repo/$id",
                sizeBytes = 100L,
                sha256 = "hash",
                capabilities = listOf(ModelCapability.TEXT),
                minSdk = 31,
                minDeviceMemoryGb = 8,
                maxContextLength = 2048,
                runtime = "litert-lm",
                files = listOf(ModelFile("model.bin", 100L, "hash")),
                defaultConfig =
                    ModelDefaultConfig(
                        topK = 1,
                        topP = 1f,
                        temperature = 1f,
                        maxTokens = 128,
                        accelerators = listOf("cpu"),
                    ),
            ),
        status = status,
        filePath = null,
        downloadedAt = null,
    )

private fun builtInModel(id: String): Model =
    Model(
        definition =
            ModelDefinition(
                id = id,
                name = id,
                description = "demo",
                source = ModelSource.BUILT_IN,
                version = "1",
                huggingfaceRepo = "demo/$id",
                sizeBytes = 0L,
                sha256 = "",
                capabilities = listOf(ModelCapability.TEXT),
                minSdk = 31,
                minDeviceMemoryGb = 0,
                maxContextLength = 2048,
                runtime = "demo",
                files = emptyList(),
                defaultConfig =
                    ModelDefaultConfig(
                        topK = 1,
                        topP = 1f,
                        temperature = 1f,
                        maxTokens = 128,
                        accelerators = listOf("cpu"),
                    ),
            ),
        status = ModelStatus.READY,
        filePath = null,
        downloadedAt = null,
    )
