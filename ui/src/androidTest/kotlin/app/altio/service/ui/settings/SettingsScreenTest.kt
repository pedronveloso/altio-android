/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
import app.altio.service.domain.settings.AppSettings
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest {

  @Test
  fun settingsScreen_showsLoadingStateBeforeModelsArrive() = runComposeUiTest {
    setContent { RenderSettingsScreen(isModelsLoading = true) }

    onNodeWithText("Loading model status…").assertIsDisplayed()
  }

  @Test
  fun settingsScreen_showsNoModelSelectedAfterModelsLoad() = runComposeUiTest {
    setContent { RenderSettingsScreen() }

    onNodeWithText("No model selected").assertIsDisplayed()
    onNodeWithText("Choose and download a model to enable on-device inference.").assertIsDisplayed()
  }

  @Test
  fun settingsScreen_showsModelManagerEntryPoint() = runComposeUiTest {
    setContent {
      RenderSettingsScreen(
          settings = AppSettings(activeModelId = "gemma-4-e2b-it"),
          models = SettingsModels(listOf(downloadableModel(status = ModelStatus.READY))),
      )
    }

    onNodeWithText("Active Model").assertIsDisplayed()
    onNodeWithText("Available Models").assertIsDisplayed()
    onNodeWithText("Manage Models").assertIsDisplayed()
    onNodeWithText("Download new models or refresh outdated installs.").assertIsDisplayed()
  }

  @Test
  fun settingsScreen_showsRefreshHelperForMissingActiveModelDownload() = runComposeUiTest {
    setContent {
      RenderSettingsScreen(
          settings = AppSettings(activeModelId = "gemma-4-e2b-it"),
          models = SettingsModels(listOf(downloadableModel(status = ModelStatus.NOT_DOWNLOADED))),
      )
    }

    onNodeWithText(
            "This model needs to be downloaded again to use the latest runtime-compatible build."
        )
        .assertIsDisplayed()
  }

  @Test
  fun settingsScreen_showsProgressSummaryForDownloadingModel() = runComposeUiTest {
    setContent {
      RenderSettingsScreen(
          settings = AppSettings(activeModelId = "gemma-4-e2b-it"),
          models = SettingsModels(listOf(downloadableModel(status = ModelStatus.DOWNLOADING))),
          downloadProgressByModelId =
              SettingsDownloadProgressByModelId(
                  mapOf(
                      "gemma-4-e2b-it" to
                          DownloadProgress(
                              modelId = "gemma-4-e2b-it",
                              status = DownloadStatus.DOWNLOADING,
                              bytesDownloaded = 500L,
                              totalBytes = 1_000L,
                              bytesPerSec = 100L,
                              etaMs = 5_000L,
                          )
                  )
              ),
      )
    }

    onNodeWithText("Download in progress.").assertIsDisplayed()
  }

  @Test
  fun settingsScreen_savesConfiguredServerPort() = runComposeUiTest {
    var savedPort: Int? = null

    setContent {
      RenderSettingsScreen(
          settings = AppSettings(serverPort = 52731),
          onSetServerPort = { savedPort = it },
      )
    }

    onNodeWithText("52731").performTextClearance()
    onNodeWithText("Port").performTextInput("53000")
    onNodeWithText("Save Port").performClick()

    org.junit.Assert.assertEquals(53000, savedPort)
  }

  @Test
  fun settingsScreen_showsValidationForOutOfRangePort() = runComposeUiTest {
    setContent { RenderSettingsScreen(settings = AppSettings(serverPort = 52731)) }

    onNodeWithText("52731").performTextClearance()
    onNodeWithText("Port").performTextInput("80")

    onNodeWithText("Port must be between 1024 and 65535.").assertIsDisplayed()
  }
}

@Composable
private fun RenderSettingsScreen(
    settings: AppSettings = AppSettings(),
    isModelsLoading: Boolean = false,
    models: SettingsModels = SettingsModels(emptyList()),
    downloadProgressByModelId: SettingsDownloadProgressByModelId =
        SettingsDownloadProgressByModelId(emptyMap()),
    onSetServerPort: (Int) -> Unit = {},
) {
  SettingsScreen(
      settings = settings,
      isModelsLoading = isModelsLoading,
      models = models,
      downloadProgressByModelId = downloadProgressByModelId,
      onSetStartOnBoot = {},
      onSetIdleShutdown = {},
      onSetServerPort = onSetServerPort,
      onSetAccelerator = {},
      onSetMaxTokens = {},
      onNavigateToModelManager = {},
      onNavigateToTokenManager = {},
      onNavigateToAdvanced = {},
  )
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
                capabilities = listOf(ModelCapability.TEXT),
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
