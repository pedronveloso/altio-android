/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.ui.model

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.altio.service.domain.model.DownloadProgress
import app.altio.service.domain.model.DownloadStatus
import app.altio.service.ui.device.DeviceOemGuidanceUi

@Composable
fun ModelDownloadScreen(
    modelName: String,
    progress: DownloadProgress?,
    batteryOptimizationDisabled: Boolean,
    supportsDirectBatteryExemption: Boolean,
    oemGuidance: DeviceOemGuidanceUi?,
    keepScreenAwake: Boolean,
    showInterruptionWarning: Boolean,
    onKeepScreenAwakeChange: (Boolean) -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onRequestDirectBatteryExemption: () -> Unit,
    onOpenOemSettings: () -> Unit,
    onRestartDownloadWorker: () -> Unit,
    onDownloadClick: () -> Unit,
    onUseDemoModelClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier.fillMaxSize().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    Text(text = modelName, style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(24.dp))

    ReliabilityCard(
        batteryOptimizationDisabled = batteryOptimizationDisabled,
        supportsDirectBatteryExemption = supportsDirectBatteryExemption,
        oemGuidance = oemGuidance,
        keepScreenAwake = keepScreenAwake,
        showInterruptionWarning = showInterruptionWarning,
        onKeepScreenAwakeChange = onKeepScreenAwakeChange,
        onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
        onRequestDirectBatteryExemption = onRequestDirectBatteryExemption,
        onOpenOemSettings = onOpenOemSettings,
        onRestartDownloadWorker = onRestartDownloadWorker,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(24.dp))

    if (progress == null || progress.status == DownloadStatus.CANCELLED) {
      Text(
          text = "Download required to use AI features",
          style = MaterialTheme.typography.bodyMedium,
      )
      Spacer(modifier = Modifier.height(16.dp))
    }

    ModelDownloadControls(
        progress = progress,
        modelStatus =
            if (progress?.status == DownloadStatus.SUCCESS)
                app.altio.service.domain.model.ModelStatus.READY
            else app.altio.service.domain.model.ModelStatus.NOT_DOWNLOADED,
        onDownloadClick = onDownloadClick,
        onPauseClick = onPauseClick,
        onResumeClick = onResumeClick,
        onCancelClick = onCancelClick,
        onDeleteClick = null,
        modifier = Modifier.fillMaxWidth(),
    )

    if (
        progress == null ||
            progress.status == DownloadStatus.CANCELLED ||
            progress.status == DownloadStatus.FAILED
    ) {
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedButton(onClick = onUseDemoModelClick, modifier = Modifier.fillMaxWidth()) {
        Text("Use Demo Model Instead")
      }
    }
  }
}

@Composable
private fun ReliabilityCard(
    batteryOptimizationDisabled: Boolean,
    supportsDirectBatteryExemption: Boolean,
    oemGuidance: DeviceOemGuidanceUi?,
    keepScreenAwake: Boolean,
    showInterruptionWarning: Boolean,
    onKeepScreenAwakeChange: (Boolean) -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onRequestDirectBatteryExemption: () -> Unit,
    onOpenOemSettings: () -> Unit,
    onRestartDownloadWorker: () -> Unit,
    modifier: Modifier = Modifier,
) {
  AlertCard(modifier = modifier) {
    Text("Download reliability", style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        "This download may take a while. Use the options below to reduce the chance of interruption.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Keep screen awake", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Only while this download is active.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(checked = keepScreenAwake, onCheckedChange = onKeepScreenAwakeChange)
    }

    if (!batteryOptimizationDisabled) {
      Spacer(modifier = Modifier.height(12.dp))
      Text(
          "Battery optimization is still enabled. Background downloads may pause while the phone is idle.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedButton(
          onClick =
              if (supportsDirectBatteryExemption) onRequestDirectBatteryExemption
              else onOpenBatteryOptimizationSettings,
          modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
            if (supportsDirectBatteryExemption) "Request Battery Exemption"
            else "Improve Background Reliability"
        )
      }
      if (supportsDirectBatteryExemption) {
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onOpenBatteryOptimizationSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Open Battery Settings Instead")
        }
      }
    }

    oemGuidance?.let { guidance ->
      Spacer(modifier = Modifier.height(12.dp))
      Text(guidance.title, style = MaterialTheme.typography.bodyMedium)
      Spacer(modifier = Modifier.height(4.dp))
      Text(
          guidance.summary,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedButton(onClick = onOpenOemSettings, modifier = Modifier.fillMaxWidth()) {
        Text(guidance.actionLabel)
      }
    }

    if (showInterruptionWarning) {
      Spacer(modifier = Modifier.height(12.dp))
      Text(
          "The download appears to be stalled. Device power management may have interrupted background work.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedButton(onClick = onRestartDownloadWorker, modifier = Modifier.fillMaxWidth()) {
        Text("Restart Download Worker")
      }
    }
  }
}

@Composable
private fun AlertCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
  Card(
      modifier = modifier,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(modifier = Modifier.padding(16.dp), content = content)
  }
}

internal fun formatProgress(progress: DownloadProgress): String {
  val downloadedMb = progress.bytesDownloaded / 1_048_576
  val totalMb = progress.totalBytes / 1_048_576
  val percent = (progress.fraction * 100).toInt()
  val speedKb = progress.bytesPerSec / 1_024
  return buildString {
    append("$downloadedMb MB / $totalMb MB  ($percent%)")
    if (speedKb > 0) append("  •  $speedKb KB/s")
    if (progress.etaMs > 0) append("  •  ETA ${formatEta(progress.etaMs)}")
  }
}

internal fun formatEta(etaMs: Long): String {
  val totalSec = etaMs / 1_000
  val h = totalSec / 3_600
  val m = (totalSec % 3_600) / 60
  val s = totalSec % 60
  return when {
    h > 0 -> "${h}h ${m}m"
    m > 0 -> "${m}m ${s}s"
    else -> "${s}s"
  }
}
