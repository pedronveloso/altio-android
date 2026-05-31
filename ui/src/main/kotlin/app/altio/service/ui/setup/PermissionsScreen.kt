/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.altio.service.ui.device.DeviceOemGuidanceUi

@Composable
fun PermissionsScreen(
    batteryOptimizationDisabled: Boolean,
    notificationsEnabled: Boolean,
    notificationRequestAttempted: Boolean,
    supportsDirectBatteryExemption: Boolean,
    oemGuidance: DeviceOemGuidanceUi?,
    onRequestNotifications: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onRequestDirectBatteryExemption: () -> Unit,
    onOpenOemSettings: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  val hasRecommendedRisk = !batteryOptimizationDisabled || oemGuidance != null
  val bottomCtaSpacing = 128.dp
  val notificationActionLabel = if (notificationRequestAttempted) "Open Settings" else "Grant"

  Box(modifier = modifier.fillMaxSize().padding(24.dp)) {
    Column(
        modifier =
            Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = bottomCtaSpacing),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text("Device Readiness", style = MaterialTheme.typography.headlineSmall)
      Text(
          "Before downloading the model, make sure the device is set up so the service can finish reliably.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Text("Required", style = MaterialTheme.typography.titleSmall)
      PermissionCard(
          title = "Notifications",
          rationale =
              "Required so the app can show download progress and service status. On some Samsung devices, you may need to enable notifications from system settings after denying the prompt.",
          granted = notificationsEnabled,
          grantedLabel = "Granted",
          actionLabel = notificationActionLabel,
          onRequest =
              if (notificationRequestAttempted) onOpenNotificationSettings
              else onRequestNotifications,
      )

      HorizontalDivider()

      Text("Recommended For Reliable Downloads", style = MaterialTheme.typography.titleSmall)
      PermissionCard(
          title = "Battery optimization",
          rationale =
              "Recommended so large downloads are less likely to pause when the screen turns off or the app is backgrounded.",
          granted = batteryOptimizationDisabled,
          grantedLabel = "Unrestricted",
          actionLabel =
              if (supportsDirectBatteryExemption) "Request Exemption" else "Review Settings",
          onRequest =
              if (supportsDirectBatteryExemption) onRequestDirectBatteryExemption
              else onOpenBatteryOptimizationSettings,
      )

      oemGuidance?.let { guidance ->
        HorizontalDivider()
        OemGuidanceCard(
            guidance = guidance,
            onOpenSettings = onOpenOemSettings,
        )
      }
    }

    Column(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
    ) {
      if (notificationsEnabled && hasRecommendedRisk) {
        Text(
            text =
                "You can continue now, but long downloads may pause or fail while the phone is idle until these steps are completed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
      }

      Button(
          onClick = onContinue,
          modifier = Modifier.fillMaxWidth(),
          enabled = notificationsEnabled,
      ) {
        Text("Continue")
      }
    }
  }
}

@Composable
private fun PermissionCard(
    title: String,
    rationale: String,
    granted: Boolean,
    grantedLabel: String,
    actionLabel: String,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
    showAction: Boolean = true,
) {
  Card(
      modifier = modifier.fillMaxWidth(),
      colors =
          CardDefaults.cardColors(
              containerColor =
                  if (granted) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.surfaceVariant,
          ),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      Spacer(Modifier.height(4.dp))
      Text(
          rationale,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (!granted) {
        Spacer(Modifier.height(12.dp))
        if (showAction) {
          Button(onClick = onRequest) { Text(actionLabel) }
        } else {
          Text(
              "Not granted",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.error,
          )
        }
      } else {
        Spacer(Modifier.height(8.dp))
        Text(
            grantedLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

@Composable
private fun OemGuidanceCard(
    guidance: DeviceOemGuidanceUi,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Card(
      modifier = modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(guidance.title, style = MaterialTheme.typography.titleSmall)
      Text(
          guidance.summary,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      guidance.steps.forEachIndexed { index, step ->
        Text(
            "${index + 1}. $step",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
        Text(guidance.actionLabel)
      }
    }
  }
}
