/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.ui.model

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.altio.service.domain.model.DownloadProgress
import app.altio.service.domain.model.DownloadStatus
import app.altio.service.domain.model.Model
import app.altio.service.domain.model.ModelCapability
import app.altio.service.domain.model.ModelDefaultConfig
import app.altio.service.domain.model.ModelDefinition
import app.altio.service.domain.model.ModelFile
import app.altio.service.domain.model.ModelSource
import app.altio.service.domain.model.ModelStatus
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ModelManagerScreenTest {

  @Test
  fun modelManager_showsLoadingStateBeforeModelsArrive() = runComposeUiTest {
    setContent { RenderModelManagerScreen(isModelsLoading = true) }

    onNodeWithText("Loading models…").assertIsDisplayed()
  }

  @Test
  fun modelManager_showsEmptyStateAfterModelsLoadWithNoItems() = runComposeUiTest {
    setContent { RenderModelManagerScreen(models = emptyList()) }

    onNodeWithText("No models available.").assertIsDisplayed()
  }

  @Test
  fun modelManager_showsDownloadForNotDownloadedModel() = runComposeUiTest {
    setContent {
      RenderModelManagerScreen(models = listOf(downloadableModel(ModelStatus.NOT_DOWNLOADED)))
    }

    onNodeWithText("Download").assertIsDisplayed()
  }

  @Test
  fun modelManager_showsDownloadForCancelledProgress() = runComposeUiTest {
    setContent {
      RenderModelManagerScreen(
          models = listOf(downloadableModel(ModelStatus.NOT_DOWNLOADED)),
          progressByModelId =
              mapOf(
                  "gemma-4-e2b-it" to
                      DownloadProgress(
                          modelId = "gemma-4-e2b-it",
                          bytesDownloaded = 0L,
                          totalBytes = 1_000L,
                          bytesPerSec = 0L,
                          etaMs = 0L,
                          status = DownloadStatus.CANCELLED,
                      )
              ),
      )
    }

    onNodeWithText("Download").assertIsDisplayed()
  }

  @Test
  fun modelManager_showsPauseAndCancelForDownloadingModel() = runComposeUiTest {
    setContent {
      RenderModelManagerScreen(
          models = listOf(downloadableModel(ModelStatus.DOWNLOADING)),
          progressByModelId =
              mapOf(
                  "gemma-4-e2b-it" to
                      DownloadProgress(
                          modelId = "gemma-4-e2b-it",
                          bytesDownloaded = 200L,
                          totalBytes = 1_000L,
                          bytesPerSec = 100L,
                          etaMs = 8_000L,
                          status = DownloadStatus.DOWNLOADING,
                      )
              ),
      )
    }

    onNodeWithText("Pause").assertIsDisplayed()
    onNodeWithText("Cancel").assertIsDisplayed()
  }

  @Test
  fun modelManager_showsUseOnlyForReadyModel() = runComposeUiTest {
    setContent { RenderModelManagerScreen(models = listOf(downloadableModel(ModelStatus.READY))) }

    onNodeWithText("Use").assertIsDisplayed()
    onNodeWithText("Delete").assertIsDisplayed()
  }
}

@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.ComposeUiTest.RenderModelManagerScreen(
    isModelsLoading: Boolean = false,
    models: List<Model> = emptyList(),
    progressByModelId: Map<String, DownloadProgress?> = emptyMap(),
    activeModelId: String = "demo-model",
) {
  setContent {
    ModelManagerScreen(
        isModelsLoading = isModelsLoading,
        models = ModelManagerModels(models),
        downloadProgressByModelId = ModelManagerDownloadProgressByModelId(progressByModelId),
        activeModelId = activeModelId,
        onSetActiveModel = {},
        onDownloadClick = {},
        onPauseDownload = {},
        onResumeDownload = {},
        onCancelDownload = {},
        onDeleteModel = {},
    )
  }
}

private fun downloadableModel(status: ModelStatus) =
    Model(
        definition =
            ModelDefinition(
                id = "gemma-4-e2b-it",
                name = "Gemma 4 E2B IT",
                description = "test",
                source = ModelSource.DOWNLOADED,
                version = "1",
                huggingfaceRepo = "repo",
                sizeBytes = 1L,
                sha256 = "hash",
                capabilities = listOf(ModelCapability.TEXT, ModelCapability.VISION),
                minSdk = 31,
                minDeviceMemoryGb = 8,
                maxContextLength = 2048,
                runtime = "litert-lm",
                files = listOf(ModelFile("model.bin", 1L, "hash")),
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
