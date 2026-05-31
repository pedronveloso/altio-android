/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import app.altio.service.data.notification.AppNotificationChannels

internal object NotificationPermissionHelper {
  const val postNotificationsPermission: String = "android.permission.POST_NOTIFICATIONS"

  fun ensureNotificationChannels(context: Context) {
    AppNotificationChannels.ensureAll(context)
  }

  fun areRequiredNotificationsEnabled(context: Context): Boolean =
      NotificationManagerCompat.from(context).areNotificationsEnabled()

  fun appNotificationSettingsIntent(packageName: String): Intent =
      appNotificationSettingsIntentSpec(packageName).toIntent()

  fun appDetailsSettingsIntent(packageName: String): Intent =
      appDetailsSettingsIntentSpec(packageName).toIntent()

  internal fun appNotificationSettingsIntentSpec(
      packageName: String
  ): NotificationSettingsIntentSpec =
      NotificationSettingsIntentSpec(
          action = Settings.ACTION_APP_NOTIFICATION_SETTINGS,
          packageExtraKey = Settings.EXTRA_APP_PACKAGE,
          packageExtraValue = packageName,
      )

  internal fun appDetailsSettingsIntentSpec(packageName: String): NotificationSettingsIntentSpec =
      NotificationSettingsIntentSpec(
          action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
          dataScheme = "package",
          dataSchemeSpecificPart = packageName,
      )
}

internal data class NotificationSettingsIntentSpec(
    val action: String,
    val packageExtraKey: String? = null,
    val packageExtraValue: String? = null,
    val dataScheme: String? = null,
    val dataSchemeSpecificPart: String? = null,
) {
  fun toIntent(): Intent =
      Intent(action).apply {
        if (packageExtraKey != null && packageExtraValue != null) {
          putExtra(packageExtraKey, packageExtraValue)
        }
        if (dataScheme != null && dataSchemeSpecificPart != null) {
          data = Uri.fromParts(dataScheme, dataSchemeSpecificPart, null)
        }
      }
}
