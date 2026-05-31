/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object AppNotificationChannels {
  const val SERVICE_STATUS_CHANNEL_ID = "ai_service_status"
  const val MODEL_DOWNLOADS_CHANNEL_ID = "model_downloads"
  const val SERVICE_STATUS_NOTIFICATION_ID = 1001
  const val MODEL_DOWNLOAD_NOTIFICATION_ID = 1002

  fun ensureAll(context: Context) {
    ensureServiceStatus(context)
    ensureModelDownloads(context)
  }

  fun ensureServiceStatus(context: Context) {
    context.notificationManager.createNotificationChannel(
        NotificationChannel(
                SERVICE_STATUS_CHANNEL_ID,
                "AI Service",
                NotificationManager.IMPORTANCE_LOW,
            )
            .apply { description = "Local AI HTTP server status" }
    )
  }

  fun ensureModelDownloads(context: Context) {
    context.notificationManager.createNotificationChannel(
        NotificationChannel(
                MODEL_DOWNLOADS_CHANNEL_ID,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW,
            )
            .apply { description = "Model download progress" }
    )
  }

  private val Context.notificationManager: NotificationManager
    get() = getSystemService(NotificationManager::class.java)
}
