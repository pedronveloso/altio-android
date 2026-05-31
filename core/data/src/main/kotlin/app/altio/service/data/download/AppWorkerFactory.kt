/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.download

import android.app.PendingIntent
import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import app.altio.service.data.db.ModelDao
import okhttp3.OkHttpClient

class AppWorkerFactory(
    private val modelDao: ModelDao,
    private val httpClient: OkHttpClient,
    private val openAppIntent: PendingIntent? = null,
) : WorkerFactory() {
  override fun createWorker(
      appContext: Context,
      workerClassName: String,
      workerParameters: WorkerParameters,
  ): ListenableWorker? =
      when (workerClassName) {
        ModelDownloadWorker::class.java.name ->
            ModelDownloadWorker(appContext, workerParameters, modelDao, httpClient, openAppIntent)
        else -> null
      }
}
