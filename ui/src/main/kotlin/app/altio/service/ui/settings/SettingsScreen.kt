/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.altio.service.domain.model.DownloadProgress
import app.altio.service.domain.model.DownloadStatus
import app.altio.service.domain.model.Model
import app.altio.service.domain.model.ModelSource
import app.altio.service.domain.model.ModelStatus
import app.altio.service.domain.settings.AppSettings
import kotlin.math.roundToInt

private val IDLE_OPTIONS: List<Pair<Int?, String>> =
    listOf(
        2 to "2 minutes",
        5 to "5 minutes",
        10 to "10 minutes",
        30 to "30 minutes",
        null to "Never",
    )

private val ACCELERATOR_OPTIONS = listOf("Auto", "CPU", "GPU")

@Immutable data class SettingsModels(val items: List<Model>)

@Immutable data class SettingsDownloadProgressByModelId(val items: Map<String, DownloadProgress?>)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    isModelsLoading: Boolean,
    models: SettingsModels,
    downloadProgressByModelId: SettingsDownloadProgressByModelId,
    onSetStartOnBoot: (Boolean) -> Unit,
    onSetIdleShutdown: (Int?) -> Unit,
    onSetServerPort: (Int) -> Unit,
    onSetAccelerator: (String) -> Unit,
    onSetMaxTokens: (Int) -> Unit,
    onNavigateToModelManager: () -> Unit,
    onNavigateToTokenManager: () -> Unit,
    onNavigateToAdvanced: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
        modifier = Modifier.widthIn(max = 600.dp).fillMaxHeight().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item { SectionLabel("Model") }

      item {
        ModelSection(
            isModelsLoading = isModelsLoading,
            models = models,
            downloadProgressByModelId = downloadProgressByModelId,
            activeModelId = settings.activeModelId,
            onNavigateToModelManager = onNavigateToModelManager,
        )
      }

      item { SectionLabel("Server") }

      item {
        SettingRow(label = "Start server on boot") {
          Switch(
              checked = settings.startOnBoot,
              onCheckedChange = onSetStartOnBoot,
          )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
      }

      item {
        ServerPortSetting(
            current = settings.serverPort,
            onSave = onSetServerPort,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
      }

      item {
        IdleShutdownPicker(
            current = settings.idleShutdownMinutes,
            onChange = onSetIdleShutdown,
        )
      }

      item { SectionLabel("Inference") }

      item {
        AcceleratorPicker(
            current = settings.accelerator,
            onChange = onSetAccelerator,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
      }

      item {
        Column {
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
                text = "Max Tokens",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${settings.maxTokens}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
          }
          Spacer(Modifier.height(4.dp))
          Slider(
              value = settings.maxTokens.toFloat(),
              onValueChange = { onSetMaxTokens(it.roundToInt()) },
              valueRange = 256f..8192f,
              steps = 30,
              modifier =
                  Modifier.semantics { contentDescription = "Max tokens: ${settings.maxTokens}" },
          )
        }
      }

      item {
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onNavigateToTokenManager,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) {
          Text("Manage API Tokens", fontWeight = FontWeight.Bold)
        }
      }

      item { SectionLabel("Advanced Config") }

      item {
        OutlinedButton(
            onClick = onNavigateToAdvanced,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) {
          Text("Enter Advanced Settings", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
private fun ModelSection(
    isModelsLoading: Boolean,
    models: SettingsModels,
    downloadProgressByModelId: SettingsDownloadProgressByModelId,
    activeModelId: String,
    onNavigateToModelManager: () -> Unit,
) {
  val activeModel = models.items.firstOrNull { it.definition.id == activeModelId }
  val activeProgress = activeModel?.let { downloadProgressByModelId.items[it.definition.id] }

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
        text = "Active Model",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { heading() },
    )
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
      Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (isModelsLoading) {
          Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
                text = "Loading model status…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          Text(
              text = activeModel?.definition?.name ?: "No model selected",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
          )
          Text(
              text = activeModelStatusSummary(activeModel, activeProgress),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (shouldShowRefreshMessage(activeModel)) {
            Text(
                text =
                    "This model needs to be downloaded again to use the latest runtime-compatible build.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
            )
          }
        }

        Spacer(Modifier.height(4.dp))

        // Onboarding CTA for managing/refreshing models
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
              text = "Available Models",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
          )
          Text(
              text = "Download new models or refresh outdated installs.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        OutlinedButton(
            onClick = onNavigateToModelManager,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) {
          Text("Manage Models", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

private fun activeModelStatusSummary(model: Model?, progress: DownloadProgress?): String =
    when {
      model == null -> "Choose and download a model to enable on-device inference."
      model.status == ModelStatus.READY || model.status == ModelStatus.LOADED ->
          if (model.status == ModelStatus.LOADED) "Ready and loaded." else "Ready to load."
      progress?.status == DownloadStatus.PAUSED -> "Download paused."
      progress?.status == DownloadStatus.FAILED -> "Download failed."
      progress?.status == DownloadStatus.QUEUED || progress?.status == DownloadStatus.DOWNLOADING ->
          "Download in progress."
      progress?.status == DownloadStatus.VERIFYING -> "Verifying download."
      else -> "Download required."
    }

private fun shouldShowRefreshMessage(model: Model?): Boolean =
    model?.definition?.source == ModelSource.DOWNLOADED &&
        model.status == ModelStatus.NOT_DOWNLOADED

@Composable
private fun ServerPortSetting(current: Int, onSave: (Int) -> Unit) {
  var value by remember(current) { mutableStateOf(current.toString()) }
  val parsedPort = value.toIntOrNull()
  val isValid =
      parsedPort != null && parsedPort in AppSettings.MIN_SERVER_PORT..AppSettings.MAX_SERVER_PORT
  val hasChanges = value != current.toString()
  val errorText =
      when {
        value.isBlank() -> "Enter a port between 1024 and 65535."
        parsedPort == null -> "Port must be a whole number."
        !isValid -> "Port must be between 1024 and 65535."
        else -> null
      }

  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
        text = "Server port",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(6.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
          value = value,
          onValueChange = { input -> value = input.filter(Char::isDigit).take(5) },
          label = { Text("Port") },
          singleLine = true,
          isError = hasChanges && errorText != null,
          modifier = Modifier.weight(1f),
      )

      Button(
          onClick = {
            parsedPort?.let(onSave)
            value = parsedPort?.toString() ?: value
          },
          enabled = hasChanges && isValid,
          shape = MaterialTheme.shapes.medium,
      ) {
        Text("Save Port", fontWeight = FontWeight.Bold)
      }
    }

    errorText?.let { err ->
      if (hasChanges) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = err,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
      }
    }

    if (errorText == null || !hasChanges) {
      Spacer(Modifier.height(4.dp))
      Text(
          text = "Used for the local loopback server and kept across restarts.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SectionLabel(text: String) {
  Text(
      text = text,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(top = 8.dp).semantics { heading() },
  )
}

@Composable
private fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
  Row(
      modifier =
          modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(vertical = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
    )
    content()
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdleShutdownPicker(current: Int?, onChange: (Int?) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  val label = IDLE_OPTIONS.firstOrNull { it.first == current }?.second ?: "10 minutes"

  Column {
    Text(
        text = "Idle shutdown timer",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(6.dp))
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
      OutlinedTextField(
          value = label,
          onValueChange = {},
          readOnly = true,
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
          modifier =
              Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                  .fillMaxWidth()
                  .semantics { contentDescription = "Idle shutdown timer, $label" },
      )
      ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        IDLE_OPTIONS.forEach { (minutes, name) ->
          DropdownMenuItem(
              text = { Text(name) },
              onClick = {
                onChange(minutes)
                expanded = false
              },
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AcceleratorPicker(current: String, onChange: (String) -> Unit) {
  var expanded by remember { mutableStateOf(false) }

  Column {
    Text(
        text = "Accelerator",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(6.dp))
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
      OutlinedTextField(
          value = current,
          onValueChange = {},
          readOnly = true,
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
          modifier =
              Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                  .fillMaxWidth()
                  .semantics { contentDescription = "Accelerator, $current" },
      )
      ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        ACCELERATOR_OPTIONS.forEach { option ->
          DropdownMenuItem(
              text = { Text(option) },
              onClick = {
                onChange(option)
                expanded = false
              },
          )
        }
      }
    }
  }
}
