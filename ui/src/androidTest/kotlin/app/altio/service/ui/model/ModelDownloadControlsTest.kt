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
import app.altio.service.domain.model.ModelStatus
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ModelDownloadControlsTest {

  @Test
  fun controls_showDownloadWhenProgressIsNull() = runComposeUiTest {
    setContent {
      ModelDownloadControls(
          progress = null,
          modelStatus = ModelStatus.NOT_DOWNLOADED,
          onDownloadClick = {},
          onPauseClick = {},
          onResumeClick = {},
          onCancelClick = {},
          onDeleteClick = null,
          downloadButtonLabel = "Download",
      )
    }

    onNodeWithText("Download").assertIsDisplayed()
  }

  @Test
  fun controls_showDownloadWhenProgressIsCancelled() = runComposeUiTest {
    setContent {
      ModelDownloadControls(
          progress =
              DownloadProgress(
                  modelId = "gemma-4-e2b-it",
                  bytesDownloaded = 0L,
                  totalBytes = 1_000L,
                  bytesPerSec = 0L,
                  etaMs = 0L,
                  status = DownloadStatus.CANCELLED,
              ),
          modelStatus = ModelStatus.NOT_DOWNLOADED,
          onDownloadClick = {},
          onPauseClick = {},
          onResumeClick = {},
          onCancelClick = {},
          onDeleteClick = null,
          downloadButtonLabel = "Download",
      )
    }

    onNodeWithText("Download").assertIsDisplayed()
  }

  @Test
  fun controls_showRetryWhenProgressFailed() = runComposeUiTest {
    setContent {
      ModelDownloadControls(
          progress =
              DownloadProgress(
                  modelId = "gemma-4-e2b-it",
                  bytesDownloaded = 100L,
                  totalBytes = 1_000L,
                  bytesPerSec = 0L,
                  etaMs = 0L,
                  status = DownloadStatus.FAILED,
              ),
          modelStatus = ModelStatus.NOT_DOWNLOADED,
          onDownloadClick = {},
          onPauseClick = {},
          onResumeClick = {},
          onCancelClick = {},
          onDeleteClick = null,
          downloadButtonLabel = "Download",
      )
    }

    onNodeWithText("Retry").assertIsDisplayed()
  }
}
