/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service

import app.altio.service.domain.model.DownloadProgress
import app.altio.service.domain.model.DownloadStatus
import app.altio.service.domain.model.Model
import app.altio.service.domain.model.ModelCapability
import app.altio.service.domain.model.ModelDefaultConfig
import app.altio.service.domain.model.ModelDefinition
import app.altio.service.domain.model.ModelFile
import app.altio.service.domain.model.ModelSource
import app.altio.service.domain.model.ModelStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModelManagementSummaryTest {

  @Test
  fun prioritizesActiveModelDownloadRequiredOverNewModelAvailable() {
    val activeModel = downloadableModel(id = "active", status = ModelStatus.NOT_DOWNLOADED)
    val newModel = downloadableModel(id = "new", status = ModelStatus.NOT_DOWNLOADED)

    val summary =
        deriveModelManagementSummary(
            activeModelId = "active",
            models = listOf(activeModel, newModel),
            downloadProgressByModelId = emptyMap(),
        )

    assertEquals("Model download required", summary.statusMessage)
  }

  @Test
  fun returnsDownloadRequiredForInvalidatedActiveModel() {
    val summary =
        deriveModelManagementSummary(
            activeModelId = "active",
            models = listOf(downloadableModel(id = "active", status = ModelStatus.NOT_DOWNLOADED)),
            downloadProgressByModelId = emptyMap(),
        )

    assertEquals("Model download required", summary.statusMessage)
  }

  @Test
  fun doesNotReturnNewModelAvailableWhenActiveModelIsHealthy() {
    val activeModel = downloadableModel(id = "active", status = ModelStatus.READY)
    val newModel = downloadableModel(id = "new", status = ModelStatus.NOT_DOWNLOADED)

    val summary =
        deriveModelManagementSummary(
            activeModelId = "active",
            models = listOf(activeModel, newModel),
            downloadProgressByModelId = emptyMap(),
        )

    assertEquals(false, summary.actionNeeded)
    assertEquals(null, summary.statusMessage)
  }

  @Test
  fun returnsDownloadInProgressForArbitraryModelProgress() {
    val activeModel = downloadableModel(id = "active", status = ModelStatus.READY)
    val downloadingModel = downloadableModel(id = "new", status = ModelStatus.DOWNLOADING)

    val summary =
        deriveModelManagementSummary(
            activeModelId = "active",
            models = listOf(activeModel, downloadingModel),
            downloadProgressByModelId =
                mapOf(
                    "new" to
                        DownloadProgress(
                            modelId = "new",
                            bytesDownloaded = 400L,
                            totalBytes = 1_000L,
                            bytesPerSec = 100L,
                            etaMs = 6_000L,
                            status = DownloadStatus.DOWNLOADING,
                        )
                ),
        )

    assertEquals("Download in progress", summary.statusMessage)
    assertEquals("40% • Test Model", summary.detailMessage)
  }
}

private fun downloadableModel(id: String, status: ModelStatus) =
    Model(
        definition =
            ModelDefinition(
                id = id,
                name = "Test Model",
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
