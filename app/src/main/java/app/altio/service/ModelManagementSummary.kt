/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service

import app.altio.service.domain.model.DownloadProgress
import app.altio.service.domain.model.DownloadStatus
import app.altio.service.domain.model.Model
import app.altio.service.domain.model.ModelSource
import app.altio.service.domain.model.ModelStatus

data class ModelManagementSummary(
    val actionNeeded: Boolean,
    val statusMessage: String? = null,
    val detailMessage: String? = null,
    val attentionCount: Int = 0,
)

internal fun deriveModelManagementSummary(
    activeModelId: String,
    models: List<Model>,
    downloadProgressByModelId: Map<String, DownloadProgress?>,
): ModelManagementSummary {
  val downloadableModels = models.filter { it.definition.source == ModelSource.DOWNLOADED }
  if (downloadableModels.isEmpty()) return ModelManagementSummary(actionNeeded = false)

  val activeModel = models.firstOrNull { it.definition.id == activeModelId }
  val activeProgress = activeModel?.let { downloadProgressByModelId[it.definition.id] }
  val activeNeedsDownload =
      activeModel == null ||
          (activeModel.definition.source == ModelSource.DOWNLOADED &&
              activeModel.status == ModelStatus.NOT_DOWNLOADED)

  if (activeNeedsDownload) {
    return ModelManagementSummary(
        actionNeeded = true,
        statusMessage =
            if (activeModel == null) "Choose and download a model" else "Model download required",
        detailMessage =
            if (activeModel == null) {
              availableModelDetail(downloadableModels.size)
            } else {
              "This model needs to be downloaded again to use the latest runtime-compatible build."
            },
        attentionCount =
            downloadableModels.count {
              it.needsAttention(downloadProgressByModelId[it.definition.id])
            },
    )
  }

  if (
      activeProgress?.status == DownloadStatus.PAUSED ||
          activeProgress?.status == DownloadStatus.FAILED
  ) {
    val progress = requireNotNull(activeProgress)
    val model = requireNotNull(activeModel)
    return ModelManagementSummary(
        actionNeeded = true,
        statusMessage =
            if (progress.status == DownloadStatus.PAUSED) "Download paused" else "Download failed",
        detailMessage = model.definition.name,
        attentionCount = 1,
    )
  }

  if (
      activeProgress?.status == DownloadStatus.QUEUED ||
          activeProgress?.status == DownloadStatus.DOWNLOADING ||
          activeProgress?.status == DownloadStatus.VERIFYING
  ) {
    val progress = requireNotNull(activeProgress)
    val model = requireNotNull(activeModel)
    return ModelManagementSummary(
        actionNeeded = true,
        statusMessage = "Download in progress",
        detailMessage = progressSummary(model.definition.name, progress),
        attentionCount = 1,
    )
  }

  val pausedOrFailedModel =
      downloadableModels.firstOrNull {
        val progress = downloadProgressByModelId[it.definition.id]
        progress?.status == DownloadStatus.PAUSED || progress?.status == DownloadStatus.FAILED
      }
  if (pausedOrFailedModel != null) {
    val progress = downloadProgressByModelId[pausedOrFailedModel.definition.id]
    return ModelManagementSummary(
        actionNeeded = true,
        statusMessage =
            if (progress?.status == DownloadStatus.PAUSED) "Download paused" else "Download failed",
        detailMessage = pausedOrFailedModel.definition.name,
        attentionCount =
            downloadableModels.count {
              it.needsAttention(downloadProgressByModelId[it.definition.id])
            },
    )
  }

  val downloadingModel =
      downloadableModels.firstOrNull {
        val progress = downloadProgressByModelId[it.definition.id]
        progress?.status == DownloadStatus.QUEUED ||
            progress?.status == DownloadStatus.DOWNLOADING ||
            progress?.status == DownloadStatus.VERIFYING
      }
  if (downloadingModel != null) {
    val progress = requireNotNull(downloadProgressByModelId[downloadingModel.definition.id])
    return ModelManagementSummary(
        actionNeeded = true,
        statusMessage = "Download in progress",
        detailMessage = progressSummary(downloadingModel.definition.name, progress),
        attentionCount =
            downloadableModels.count {
              it.needsAttention(downloadProgressByModelId[it.definition.id])
            },
    )
  }

  return ModelManagementSummary(actionNeeded = false)
}

private fun Model.needsAttention(progress: DownloadProgress?): Boolean =
    when {
      status == ModelStatus.NOT_DOWNLOADED -> true
      progress?.status == DownloadStatus.PAUSED -> true
      progress?.status == DownloadStatus.FAILED -> true
      progress?.status == DownloadStatus.QUEUED -> true
      progress?.status == DownloadStatus.DOWNLOADING -> true
      progress?.status == DownloadStatus.VERIFYING -> true
      else -> false
    }

private fun availableModelDetail(count: Int, fallbackName: String? = null): String =
    when {
      count <= 0 -> ""
      count == 1 && fallbackName != null -> fallbackName
      count == 1 -> "1 model available to download"
      else -> "$count models available to download"
    }

private fun progressSummary(modelName: String, progress: DownloadProgress): String {
  val percent = (progress.fraction * 100).toInt().coerceIn(0, 100)
  return "$percent% • $modelName"
}
