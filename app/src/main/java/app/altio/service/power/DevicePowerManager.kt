/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.power

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

class DevicePowerManager(
    private val context: Context,
    private val allowDirectBatteryExemptionRequest: Boolean = false,
) {

  fun detectOem(): OemFamily = detectOemFamily(Build.MANUFACTURER, Build.BRAND)

  fun isBatteryOptimizationDisabled(): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
  }

  fun batteryOptimizationSettingsIntent(): Intent =
      Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  @SuppressLint("BatteryLife")
  fun directBatteryExemptionIntent(): Intent? {
    if (!allowDirectBatteryExemptionRequest) return null
    return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
      data = "package:${context.packageName}".toUri()
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  fun appDetailsSettingsIntent(): Intent =
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
          .setData("package:${context.packageName}".toUri())
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  fun oemGuidance(): OemGuidance? =
      when (detectOem()) {
        OemFamily.XIAOMI,
        OemFamily.REDMI,
        OemFamily.POCO -> xiaomiStyleGuidance(detectOem())
        OemFamily.UNKNOWN -> null
      }

  private fun xiaomiStyleGuidance(family: OemFamily): OemGuidance {
    val deviceLabel =
        when (family) {
          OemFamily.XIAOMI -> "Xiaomi"
          OemFamily.REDMI -> "Redmi"
          OemFamily.POCO -> "POCO"
          OemFamily.UNKNOWN -> "this device"
        }
    return OemGuidance(
        family = family,
        deviceLabel = deviceLabel,
        title = "$deviceLabel devices may stop long downloads",
        summary =
            "Set this app to unrestricted battery use before downloading the model so the system is less likely to pause background work.",
        steps =
            listOf(
                OemGuidanceStep(
                    title = "Battery",
                    detail = "Set this app to No restrictions or Unrestricted battery usage.",
                ),
                OemGuidanceStep(
                    title = "Autostart",
                    detail = "Enable Autostart for this app if your settings screen shows it.",
                ),
                OemGuidanceStep(
                    title = "Task management",
                    detail = "Do not clear the app from recents while the model is downloading.",
                ),
            ),
        settingsIntents =
            listOf(
                Intent()
                    .setClassName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity",
                    ),
                Intent()
                    .setClassName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
                    )
                    .putExtra("package_name", context.packageName),
                appDetailsSettingsIntent(),
            ),
    )
  }
}
