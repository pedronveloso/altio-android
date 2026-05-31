/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.model

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.altio.service.data.catalog.ModelCatalog
import app.altio.service.data.db.ModelDao
import app.altio.service.data.db.ModelEntity
import app.altio.service.data.download.ModelDownloadWorker
import app.altio.service.domain.model.DownloadFailureReason
import app.altio.service.domain.model.DownloadProgress
import app.altio.service.domain.model.DownloadStatus
import app.altio.service.domain.model.Model
import app.altio.service.domain.model.ModelDefinition
import app.altio.service.domain.model.ModelRepository
import app.altio.service.domain.model.ModelSource
import app.altio.service.domain.model.ModelStatus
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

class ModelRepositoryImpl(
    private val modelDao: ModelDao,
    private val workManager: WorkManager,
    private val appContext: Context,
) : ModelRepository {

  override fun getAvailableModels(): Flow<List<Model>> =
      modelDao.observeAll().map { entities ->
        ModelCatalog.all.map { definition ->
          val entity = entities.firstOrNull { it.id == definition.id }
          definition.toModel(entity)
        }
      }

  override fun getModel(id: String): Flow<Model?> =
      modelDao.observe(id).map { entity ->
        val definition = ModelCatalog.find(id) ?: return@map null
        definition.toModel(entity)
      }

  override fun getDownloadProgress(id: String): Flow<DownloadProgress> {
    val definition = ModelCatalog.find(id)
    val totalBytes = definition?.sizeBytes ?: 0L
    return combine(
        workManager.getWorkInfosByTagFlow(downloadTag(id)),
        modelDao.observe(id),
    ) { infos, entity ->
      val info = infos.firstOrNull()
      val wmDone = info == null || info.state == WorkInfo.State.CANCELLED
      when {
        wmDone && entity?.status == ModelStatus.PAUSED -> {
          val tmpFile = tmpFileFor(id, definition)
          DownloadProgress(
              modelId = id,
              bytesDownloaded = if (tmpFile != null && tmpFile.exists()) tmpFile.length() else 0L,
              totalBytes = totalBytes,
              bytesPerSec = 0L,
              etaMs = 0L,
              status = DownloadStatus.PAUSED,
          )
        }
        info == null ->
            DownloadProgress(
                modelId = id,
                bytesDownloaded = 0L,
                totalBytes = totalBytes,
                bytesPerSec = 0L,
                etaMs = 0L,
                status = DownloadStatus.CANCELLED,
            )
        else -> info.toDownloadProgress(id)
      }
    }
  }

  override suspend fun startDownload(id: String) {
    val definition = ModelCatalog.find(id) ?: return
    if (definition.source == ModelSource.BUILT_IN) return
    modelDao.upsert(
        ModelEntity(
            id = id,
            name = definition.name,
            version = definition.version,
            sizeBytes = definition.sizeBytes,
            status = ModelStatus.DOWNLOADING,
            downloadedAt = null,
            filePath = null,
        )
    )
    val request =
        OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to id))
            .addTag(downloadTag(id))
            .build()
    workManager.enqueueUniqueWork(downloadTag(id), ExistingWorkPolicy.KEEP, request)
  }

  override suspend fun pauseDownload(id: String) {
    if (ModelCatalog.find(id)?.source == ModelSource.BUILT_IN) return
    workManager.cancelUniqueWork(downloadTag(id))
    modelDao.updateStatus(id, ModelStatus.PAUSED)
  }

  override suspend fun resumeDownload(id: String) {
    val definition = ModelCatalog.find(id) ?: return
    if (definition.source == ModelSource.BUILT_IN) return
    modelDao.updateStatus(id, ModelStatus.DOWNLOADING)
    val request =
        OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to id))
            .addTag(downloadTag(id))
            .build()
    // REPLACE because WM still holds the old CANCELLED record; KEEP would silently no-op.
    workManager.enqueueUniqueWork(downloadTag(id), ExistingWorkPolicy.REPLACE, request)
  }

  override suspend fun cancelDownload(id: String) {
    if (ModelCatalog.find(id)?.source == ModelSource.BUILT_IN) return
    Timber.i("Cancelling download for model %s", id)
    workManager.cancelUniqueWork(downloadTag(id))
    val tmpFile = tmpFileFor(id, ModelCatalog.find(id))
    if (tmpFile?.exists() == true) {
      tmpFile.delete()
      Timber.i("Deleted partial download file for model %s", id)
    }
    modelDao.updateStatus(id, ModelStatus.NOT_DOWNLOADED)
  }

  override suspend fun deleteModel(id: String) {
    if (ModelCatalog.find(id)?.source == ModelSource.BUILT_IN) return
    val entity = modelDao.observe(id).first()
    entity?.filePath?.let { path ->
      val modelFile = File(path)
      if (modelFile.exists() && !modelFile.delete()) {
        Timber.w("Failed to delete model file for %s at %s", id, path)
      }
      val versionDir = modelFile.parentFile
      if (versionDir?.exists() == true && versionDir.listFiles().isNullOrEmpty()) {
        versionDir.delete()
      }
      val modelDir = versionDir?.parentFile
      if (modelDir?.exists() == true && modelDir.listFiles().isNullOrEmpty()) {
        modelDir.delete()
      }
    }
    tmpFileFor(id, ModelCatalog.find(id))?.takeIf(File::exists)?.delete()
    modelDao.delete(id)
  }

  override suspend fun setStatus(id: String, status: ModelStatus) {
    if (ModelCatalog.find(id)?.source == ModelSource.BUILT_IN) return
    modelDao.updateStatus(id, status)
  }

  override suspend fun verifyModel(id: String): Boolean {
    val definition = ModelCatalog.find(id) ?: return false
    if (definition.source == ModelSource.BUILT_IN) return true
    val entity = modelDao.observe(id).first() ?: return false
    val filePath = entity.filePath ?: return false
    val modelFile = definition.files.first()
    val file = File(filePath)
    return app.altio.service.data.download.Sha256Verifier.verify(file, modelFile.sha256)
  }

  override suspend fun reconcileModelsOnStartup() {
    val storageRoot = appContext.getExternalFilesDir(null)
    if (storageRoot == null) {
      Timber.w("Skipping model reconciliation because external files dir is unavailable")
      return
    }

    ModelCatalog.all
        .filter { it.source == ModelSource.DOWNLOADED }
        .forEach { definition ->
          val expectedFile = finalFileFor(definition)
          val entity = modelDao.observe(definition.id).first()
          invalidateLegacyGemmaInstall(definition, entity)
          when {
            expectedFile.exists() && expectedFile.length() == definition.sizeBytes -> {
              val downloadedAt =
                  entity?.downloadedAt?.takeIf { it > 0L } ?: expectedFile.lastModified()
              modelDao.upsert(
                  ModelEntity(
                      id = definition.id,
                      name = definition.name,
                      version = definition.version,
                      sizeBytes = definition.sizeBytes,
                      status = ModelStatus.READY,
                      downloadedAt = downloadedAt.takeIf { it > 0L },
                      filePath = expectedFile.absolutePath,
                  )
              )
              Timber.i(
                  "Reconciled model %s from disk at %s",
                  definition.id,
                  expectedFile.absolutePath,
              )
            }
            entity?.filePath != null ||
                entity?.status == ModelStatus.READY ||
                entity?.status == ModelStatus.LOADED -> {
              modelDao.clearDownloadedState(definition.id, ModelStatus.NOT_DOWNLOADED)
              Timber.w(
                  "Cleared stale model metadata for %s because %s was not found",
                  definition.id,
                  expectedFile.absolutePath,
              )
            }
          }
        }
  }

  private suspend fun invalidateLegacyGemmaInstall(
      definition: ModelDefinition,
      entity: ModelEntity?,
  ) {
    if (definition.id != ModelCatalog.gemma4E2bIt.id) return

    val legacyFile = legacyGemmaFileFor(definition)
    val legacyFilePathSegment =
        "/models/${definition.id}/${ModelCatalog.GEMMA_4_E2B_IT_LEGACY_VERSION}/"
    val pointsAtLegacyInstall =
        entity?.version == ModelCatalog.GEMMA_4_E2B_IT_LEGACY_VERSION ||
            entity?.filePath?.contains(legacyFilePathSegment) == true

    if (!legacyFile.exists() && !pointsAtLegacyInstall) return

    if (
        entity?.filePath != null ||
            entity?.status == ModelStatus.READY ||
            entity?.status == ModelStatus.LOADED
    ) {
      modelDao.clearDownloadedState(definition.id, ModelStatus.NOT_DOWNLOADED)
    }
    deleteVersionDirectory(legacyFile.parentFile)
    Timber.w(
        "Invalidated stale Gemma 4 install for %s at legacy version %s",
        definition.id,
        ModelCatalog.GEMMA_4_E2B_IT_LEGACY_VERSION,
    )
  }

  private fun downloadTag(modelId: String) = "download:$modelId"

  private fun finalFileFor(definition: ModelDefinition): File {
    val fileName =
        requireNotNull(definition.files.firstOrNull()?.name) {
          "Downloaded model ${definition.id} must declare a file"
        }
    return File(
        appContext.getExternalFilesDir(null),
        "models/${definition.id}/${definition.version}/$fileName",
    )
  }

  private fun legacyGemmaFileFor(definition: ModelDefinition): File {
    val fileName =
        requireNotNull(definition.files.firstOrNull()?.name) {
          "Downloaded model ${definition.id} must declare a file"
        }
    return File(
        appContext.getExternalFilesDir(null),
        "models/${definition.id}/${ModelCatalog.GEMMA_4_E2B_IT_LEGACY_VERSION}/$fileName",
    )
  }

  private fun deleteVersionDirectory(versionDir: File?) {
    if (versionDir?.exists() != true) return
    if (!versionDir.deleteRecursively()) {
      Timber.w("Failed to delete stale model directory at %s", versionDir.absolutePath)
    }
    val modelDir = versionDir.parentFile
    if (modelDir?.exists() == true && modelDir.listFiles().isNullOrEmpty()) {
      modelDir.delete()
    }
  }

  private fun tmpFileFor(id: String, definition: ModelDefinition?): File? {
    val fileName = definition?.files?.firstOrNull()?.name ?: return null
    return File(appContext.getExternalFilesDir(null), "models/$id/$fileName.tmp")
  }

  private fun ModelDefinition.toModel(entity: ModelEntity?): Model =
      if (source == ModelSource.BUILT_IN) {
        Model(
            definition = this,
            status = ModelStatus.READY,
            filePath = null,
            downloadedAt = null,
        )
      } else {
        Model(
            definition = this,
            status = entity?.status ?: ModelStatus.NOT_DOWNLOADED,
            filePath = entity?.filePath,
            downloadedAt = entity?.downloadedAt,
        )
      }

  private fun WorkInfo.toDownloadProgress(modelId: String): DownloadProgress {
    val totalBytes = ModelCatalog.find(modelId)?.sizeBytes ?: 0L
    val bytesDownloaded = progress.getLong(ModelDownloadWorker.KEY_BYTES_DOWNLOADED, 0L)
    val bytesPerSec = progress.getLong(ModelDownloadWorker.KEY_BYTES_PER_SEC, 0L)
    val etaMs = if (bytesPerSec > 0) (totalBytes - bytesDownloaded) * 1_000 / bytesPerSec else 0L
    val status =
        when (state) {
          WorkInfo.State.ENQUEUED -> DownloadStatus.QUEUED
          WorkInfo.State.RUNNING -> DownloadStatus.DOWNLOADING
          WorkInfo.State.SUCCEEDED -> DownloadStatus.SUCCESS
          WorkInfo.State.FAILED -> DownloadStatus.FAILED
          WorkInfo.State.CANCELLED -> DownloadStatus.CANCELLED
          WorkInfo.State.BLOCKED -> DownloadStatus.QUEUED
        }
    val failureReason =
        if (state == WorkInfo.State.FAILED) {
          outputData.getString(ModelDownloadWorker.KEY_FAILURE_REASON)?.let {
            runCatching { DownloadFailureReason.valueOf(it) }.getOrNull()
          }
        } else null
    return DownloadProgress(
        modelId = modelId,
        bytesDownloaded = bytesDownloaded,
        totalBytes = totalBytes,
        bytesPerSec = bytesPerSec,
        etaMs = etaMs,
        status = status,
        failureReason = failureReason,
    )
  }
}
