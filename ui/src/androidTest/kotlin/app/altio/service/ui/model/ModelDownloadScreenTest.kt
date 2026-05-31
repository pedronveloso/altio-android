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
import app.altio.service.ui.device.DeviceOemGuidanceUi
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ModelDownloadScreenTest {

  @Test
  fun downloadScreen_showsDownloadButtonWhenIdle() = runComposeUiTest {
    setContent {
      ModelDownloadScreen(
          modelName = "Gemma 3n E2B IT INT4",
          progress = null,
          batteryOptimizationDisabled = true,
          supportsDirectBatteryExemption = false,
          oemGuidance = null,
          keepScreenAwake = false,
          showInterruptionWarning = false,
          onKeepScreenAwakeChange = {},
          onOpenBatteryOptimizationSettings = {},
          onRequestDirectBatteryExemption = {},
          onOpenOemSettings = {},
          onRestartDownloadWorker = {},
          onDownloadClick = {},
          onUseDemoModelClick = {},
          onPauseClick = {},
          onResumeClick = {},
          onCancelClick = {},
      )
    }

    onNodeWithText("Gemma 3n E2B IT INT4").assertIsDisplayed()
    onNodeWithText("Download Model").assertIsDisplayed()
  }

  @Test
  fun downloadScreen_showsProgressWhenDownloading() = runComposeUiTest {
    val progress =
        DownloadProgress(
            modelId = "gemma-3n-e2b-it-int4",
            status = DownloadStatus.DOWNLOADING,
            bytesDownloaded = 420_000_000L,
            totalBytes = 1_000_000_000L,
            bytesPerSec = 5_000_000L,
            etaMs = 120_000L,
        )
    setContent {
      ModelDownloadScreen(
          modelName = "Gemma 3n E2B IT INT4",
          progress = progress,
          batteryOptimizationDisabled = false,
          supportsDirectBatteryExemption = true,
          oemGuidance =
              DeviceOemGuidanceUi(
                  title = "POCO devices may stop long downloads",
                  summary = "Review your device settings before downloading.",
                  steps = listOf("Battery", "Autostart"),
              ),
          keepScreenAwake = false,
          showInterruptionWarning = true,
          onKeepScreenAwakeChange = {},
          onOpenBatteryOptimizationSettings = {},
          onRequestDirectBatteryExemption = {},
          onOpenOemSettings = {},
          onRestartDownloadWorker = {},
          onDownloadClick = {},
          onUseDemoModelClick = {},
          onPauseClick = {},
          onResumeClick = {},
          onCancelClick = {},
      )
    }

    onNodeWithText("Pause").assertIsDisplayed()
    onNodeWithText("Cancel").assertIsDisplayed()
    onNodeWithText("Request Battery Exemption").assertIsDisplayed()
    onNodeWithText("Restart Download Worker").assertIsDisplayed()
  }

  @Test
  fun downloadScreen_showsResumeWhenPaused() = runComposeUiTest {
    val progress =
        DownloadProgress(
            modelId = "gemma-3n-e2b-it-int4",
            status = DownloadStatus.PAUSED,
            bytesDownloaded = 420_000_000L,
            totalBytes = 1_000_000_000L,
            bytesPerSec = 0L,
            etaMs = 0L,
        )
    setContent {
      ModelDownloadScreen(
          modelName = "Gemma 3n E2B IT INT4",
          progress = progress,
          batteryOptimizationDisabled = true,
          supportsDirectBatteryExemption = false,
          oemGuidance = null,
          keepScreenAwake = false,
          showInterruptionWarning = false,
          onKeepScreenAwakeChange = {},
          onOpenBatteryOptimizationSettings = {},
          onRequestDirectBatteryExemption = {},
          onOpenOemSettings = {},
          onRestartDownloadWorker = {},
          onDownloadClick = {},
          onUseDemoModelClick = {},
          onPauseClick = {},
          onResumeClick = {},
          onCancelClick = {},
      )
    }

    onNodeWithText("Resume").assertIsDisplayed()
    onNodeWithText("Cancel").assertIsDisplayed()
  }

  @Test
  fun downloadScreen_showsCompleteMessageOnSuccess() = runComposeUiTest {
    val progress =
        DownloadProgress(
            modelId = "gemma-3n-e2b-it-int4",
            status = DownloadStatus.SUCCESS,
            bytesDownloaded = 1_000_000_000L,
            totalBytes = 1_000_000_000L,
            bytesPerSec = 0L,
            etaMs = 0L,
        )
    setContent {
      ModelDownloadScreen(
          modelName = "Gemma 3n E2B IT INT4",
          progress = progress,
          batteryOptimizationDisabled = true,
          supportsDirectBatteryExemption = false,
          oemGuidance = null,
          keepScreenAwake = false,
          showInterruptionWarning = false,
          onKeepScreenAwakeChange = {},
          onOpenBatteryOptimizationSettings = {},
          onRequestDirectBatteryExemption = {},
          onOpenOemSettings = {},
          onRestartDownloadWorker = {},
          onDownloadClick = {},
          onUseDemoModelClick = {},
          onPauseClick = {},
          onResumeClick = {},
          onCancelClick = {},
      )
    }

    onNodeWithText("Download complete").assertIsDisplayed()
  }
}
