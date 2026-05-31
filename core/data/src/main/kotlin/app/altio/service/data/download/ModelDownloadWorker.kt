/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.download

import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.altio.service.data.catalog.ModelCatalog
import app.altio.service.data.db.ModelDao
import app.altio.service.data.db.ModelEntity
import app.altio.service.data.notification.AppNotificationChannels
import app.altio.service.domain.model.DownloadFailureReason
import app.altio.service.domain.model.ModelStatus
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber

class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
    private val modelDao: ModelDao,
    private val httpClient: OkHttpClient,
    private val openAppIntent: PendingIntent? = null,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val modelId =
        inputData.getString(KEY_MODEL_ID)
            ?: return Result.failure(
                workDataOf(KEY_FAILURE_REASON to DownloadFailureReason.MISSING_MODEL_ID.name)
            )
    val definition =
        ModelCatalog.find(modelId)
            ?: return Result.failure(
                workDataOf(KEY_FAILURE_REASON to DownloadFailureReason.UNKNOWN_MODEL.name)
            )
    Timber.i("Download starting for model %s", modelId)
    val modelFile = definition.files.first()

    AppNotificationChannels.ensureModelDownloads(applicationContext)
    setForeground(buildForegroundInfo(definition.name, 0))

    val tmpFile = getTmpFile(modelId, modelFile.name)
    val finalFile = getFinalFile(modelId, definition.version, modelFile.name)

    modelDao.upsert(
        ModelEntity(
            id = modelId,
            name = definition.name,
            version = definition.version,
            sizeBytes = definition.sizeBytes,
            status = ModelStatus.DOWNLOADING,
            downloadedAt = null,
            filePath = null,
        )
    )

    try {
      downloadFile(
          url = downloadUrl(definition.huggingfaceRepo, modelFile.name),
          tmpFile = tmpFile,
          totalBytes = modelFile.sizeBytes,
      ) { downloaded, total, bytesPerSec ->
        val fraction = if (total > 0) (downloaded * 100 / total).toInt() else 0
        setProgress(
            workDataOf(
                KEY_BYTES_DOWNLOADED to downloaded,
                KEY_TOTAL_BYTES to total,
                KEY_BYTES_PER_SEC to bytesPerSec,
            )
        )
        setForeground(buildForegroundInfo(definition.name, fraction))
      }
    } catch (e: IOException) {
      return if (runAttemptCount < MAX_RETRIES) {
        Timber.w(
            e,
            "Download failed for %s (attempt %d/%d) — retrying",
            modelId,
            runAttemptCount + 1,
            MAX_RETRIES,
        )
        // Keep status DOWNLOADING; tmp file is preserved so the next attempt resumes.
        Result.retry()
      } else {
        Timber.e(e, "Download failed for %s after %d attempts — giving up", modelId, MAX_RETRIES)
        modelDao.updateStatus(modelId, ModelStatus.NOT_DOWNLOADED)
        Result.failure(workDataOf(KEY_FAILURE_REASON to DownloadFailureReason.NETWORK_ERROR.name))
      }
    }

    modelDao.updateStatus(modelId, ModelStatus.VERIFYING)

    if (!Sha256Verifier.verify(tmpFile, modelFile.sha256)) {
      Timber.e("SHA256 verification failed for model %s — deleting tmp file", modelId)
      tmpFile.delete()
      modelDao.updateStatus(modelId, ModelStatus.NOT_DOWNLOADED)
      return Result.failure(
          workDataOf(KEY_FAILURE_REASON to DownloadFailureReason.CHECKSUM_MISMATCH.name)
      )
    }

    finalFile.parentFile?.mkdirs()
    try {
      Files.move(tmpFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    } catch (e: IOException) {
      Timber.e(e, "Failed to finalize model file for %s", modelId)
      modelDao.updateStatus(modelId, ModelStatus.NOT_DOWNLOADED)
      return Result.failure(
          workDataOf(KEY_FAILURE_REASON to DownloadFailureReason.FILE_SYSTEM_ERROR.name)
      )
    }

    modelDao.updateReady(
        id = modelId,
        status = ModelStatus.READY,
        filePath = finalFile.absolutePath,
        downloadedAt = System.currentTimeMillis(),
    )

    Timber.i("Model %s downloaded and verified", modelId)
    return Result.success()
  }

  private suspend fun downloadFile(
      url: String,
      tmpFile: File,
      totalBytes: Long,
      onProgress: suspend (downloaded: Long, total: Long, bytesPerSec: Long) -> Unit,
  ) {
    val startByte = if (tmpFile.exists()) tmpFile.length() else 0L
    Timber.i(
        "Starting model download for URL: %s (Expected size: %d bytes, Start byte: %d)",
        url,
        totalBytes,
        startByte,
    )
    if (startByte == totalBytes && totalBytes > 0L) {
      Timber.i("Partial file already matches expected size for %s — skipping download", url)
      onProgress(totalBytes, totalBytes, 0L)
      return
    }
    val rangeRequested = startByte > 0
    var effectiveStart = startByte
    var response = executeDownloadRequest(url, startByte)
    val contentRange = response.header("Content-Range")

    if (
        ModelDownloadWorker.shouldRestartFromZeroOnResume(
            rangeRequested = rangeRequested,
            responseCode = response.code,
            contentRange = contentRange,
            expectedStart = startByte,
        )
    ) {
      if (response.code == 200) {
        Timber.w("Server ignored Range header for %s — restarting from 0", url)
      } else {
        Timber.w(
            "Server returned unexpected Content-Range '%s' for %s at byte %d — restarting from 0",
            contentRange,
            url,
            startByte,
        )
      }
      response.close()
      tmpFile.writeBytes(ByteArray(0))
      effectiveStart = 0L
      response = executeDownloadRequest(url, effectiveStart)
    }

    Timber.i(
        "HTTP response received for %s: Code %d, Content-Length: %s, Content-Range: %s",
        url,
        response.code,
        response.header("Content-Length"),
        response.header("Content-Range"),
    )

    if (!response.isSuccessful && response.code != 206) {
      response.close()
      throw IOException("HTTP ${response.code}")
    }
    if (response.code == 206) {
      val resumedContentRange = response.header("Content-Range")
      if (!ModelDownloadWorker.contentRangeStartsAt(resumedContentRange, effectiveStart)) {
        response.close()
        throw IOException(
            "Unexpected Content-Range '$resumedContentRange' when resuming at byte $effectiveStart"
        )
      }
    }

    if (effectiveStart > 0) Timber.d("Resuming download from byte %d", effectiveStart)

    val body = response.body
    tmpFile.parentFile?.mkdirs()

    val speedSamples = ArrayDeque<Pair<Long, Long>>() // timestamp → cumulative bytes
    var lastProgressMs = System.currentTimeMillis()
    var totalDownloaded = effectiveStart
    var smoothedBytesPerSec = 0L

    withContext(Dispatchers.IO) {
      FileOutputStream(tmpFile, effectiveStart > 0).use { out ->
        body.byteStream().use { input ->
          val buffer = ByteArray(8_192)
          var bytesRead: Int
          while (input.read(buffer).also { bytesRead = it } != -1) {
            out.write(buffer, 0, bytesRead)
            totalDownloaded += bytesRead

            val now = System.currentTimeMillis()
            speedSamples.addLast(Pair(now, totalDownloaded))
            if (speedSamples.size > 5) speedSamples.removeFirst()

            if (now - lastProgressMs >= PROGRESS_INTERVAL_MS) {
              val rawSpeed = ModelDownloadWorker.calculateSpeed(speedSamples)
              smoothedBytesPerSec =
                  if (smoothedBytesPerSec == 0L) rawSpeed
                  else ModelDownloadWorker.applyEma(EMA_ALPHA, rawSpeed, smoothedBytesPerSec)
              onProgress(totalDownloaded, totalBytes, smoothedBytesPerSec)
              lastProgressMs = now
            }
          }
        }
      }
    }
    Timber.i(
        "Finished file download for %s. Total written: %d bytes. File saved to: %s",
        url,
        totalDownloaded,
        tmpFile.absolutePath,
    )
  }

  private suspend fun executeDownloadRequest(url: String, startByte: Long): Response {
    val requestBuilder = Request.Builder().url(url)
    if (startByte > 0) requestBuilder.header("Range", "bytes=$startByte-")
    return withContext(Dispatchers.IO) { httpClient.newCall(requestBuilder.build()).execute() }
  }

  private fun getTmpFile(modelId: String, fileName: String): File =
      File(applicationContext.getExternalFilesDir(null), "models/$modelId/$fileName.tmp")

  private fun getFinalFile(modelId: String, version: String, fileName: String): File =
      File(applicationContext.getExternalFilesDir(null), "models/$modelId/$version/$fileName")

  private fun buildForegroundInfo(modelName: String, progressPercent: Int): ForegroundInfo {
    val notification =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading $modelName")
            .setProgress(100, progressPercent, progressPercent == 0)
            .setOngoing(true)
            .apply { openAppIntent?.let { setContentIntent(it) } }
            .build()
    return ForegroundInfo(
        AppNotificationChannels.MODEL_DOWNLOAD_NOTIFICATION_ID,
        notification,
        FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )
  }

  companion object {
    const val KEY_MODEL_ID = "model_id"
    const val KEY_BYTES_DOWNLOADED = "bytes_downloaded"
    const val KEY_TOTAL_BYTES = "total_bytes"
    const val KEY_BYTES_PER_SEC = "bytes_per_sec"
    const val KEY_FAILURE_REASON = "failure_reason"

    private const val CHANNEL_ID = AppNotificationChannels.MODEL_DOWNLOADS_CHANNEL_ID
    private const val PROGRESS_INTERVAL_MS = 200L
    private const val MAX_RETRIES = 5
    private const val EMA_ALPHA = 0.15

    internal fun calculateSpeed(samples: ArrayDeque<Pair<Long, Long>>): Long {
      if (samples.size < 2) return 0L
      val oldest = samples.first()
      val newest = samples.last()
      val timeDeltaMs = (newest.first - oldest.first).coerceAtLeast(1)
      return (newest.second - oldest.second) * 1_000 / timeDeltaMs
    }

    internal fun applyEma(alpha: Double, raw: Long, smoothed: Long): Long =
        (alpha * raw + (1 - alpha) * smoothed).toLong()

    internal fun contentRangeStartsAt(contentRange: String?, expectedStart: Long): Boolean {
      if (contentRange.isNullOrBlank()) return false
      val normalized = contentRange.trim().lowercase(Locale.US)
      val prefix = "bytes $expectedStart-"
      return normalized.startsWith(prefix)
    }

    internal fun shouldRestartFromZeroOnResume(
        rangeRequested: Boolean,
        responseCode: Int,
        contentRange: String?,
        expectedStart: Long,
    ): Boolean {
      if (!rangeRequested) return false
      if (responseCode == 200) return true
      return responseCode == 206 && !contentRangeStartsAt(contentRange, expectedStart)
    }

    fun downloadUrl(huggingfaceRepo: String, fileName: String): String =
        "https://huggingface.co/$huggingfaceRepo/resolve/main/$fileName"
  }
}
