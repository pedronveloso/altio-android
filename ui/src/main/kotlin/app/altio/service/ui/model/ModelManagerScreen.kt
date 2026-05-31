/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.ui.model

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.altio.service.domain.model.DownloadProgress
import app.altio.service.domain.model.Model
import app.altio.service.domain.model.ModelCapability
import app.altio.service.domain.model.ModelSource
import app.altio.service.domain.model.ModelStatus

@Immutable data class ModelManagerModels(val items: List<Model>)

@Immutable
data class ModelManagerDownloadProgressByModelId(val items: Map<String, DownloadProgress?>)

@Composable
fun ModelManagerScreen(
    isModelsLoading: Boolean,
    models: ModelManagerModels,
    downloadProgressByModelId: ModelManagerDownloadProgressByModelId,
    activeModelId: String,
    onSetActiveModel: (String) -> Unit,
    onDownloadClick: (modelId: String) -> Unit,
    onPauseDownload: (modelId: String) -> Unit,
    onResumeDownload: (modelId: String) -> Unit,
    onCancelDownload: (modelId: String) -> Unit,
    onDeleteModel: (modelId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
  if (isModelsLoading) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Loading models…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    return
  }

  if (models.items.isEmpty()) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(
          "No models available.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    return
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
        modifier = Modifier.widthIn(max = 600.dp).fillMaxHeight().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item {
        Text(
            "Models",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
      }
      items(models.items, key = { it.definition.id }) { model ->
        ModelCard(
            model = model,
            progress = downloadProgressByModelId.items[model.definition.id],
            isActive = activeModelId == model.definition.id,
            onSetActiveModel = onSetActiveModel,
            onDownloadClick = onDownloadClick,
            onPauseDownload = onPauseDownload,
            onResumeDownload = onResumeDownload,
            onCancelDownload = onCancelDownload,
            onDeleteModel = onDeleteModel,
        )
      }
    }
  } // Box
}

@Composable
private fun ModelCard(
    model: Model,
    progress: DownloadProgress?,
    isActive: Boolean,
    onSetActiveModel: (String) -> Unit,
    onDownloadClick: (modelId: String) -> Unit,
    onPauseDownload: (modelId: String) -> Unit,
    onResumeDownload: (modelId: String) -> Unit,
    onCancelDownload: (modelId: String) -> Unit,
    onDeleteModel: (modelId: String) -> Unit,
) {
  val isReady = model.status == ModelStatus.READY || model.status == ModelStatus.LOADED

  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(text = model.definition.name, style = MaterialTheme.typography.titleMedium)
      Text(text = model.definition.description, style = MaterialTheme.typography.bodySmall)
      Text(
          text = buildMetadata(model),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (isActive) {
        Text(
            "Active Model",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
      }
      OutlinedButton(
          onClick = { onSetActiveModel(model.definition.id) },
          enabled = isReady && !isActive,
      ) {
        Text(if (isActive) "In Use" else "Use")
      }
      if (model.definition.source == ModelSource.DOWNLOADED) {
        ModelDownloadControls(
            progress = progress,
            modelStatus = model.status,
            onDownloadClick = { onDownloadClick(model.definition.id) },
            onPauseClick = { onPauseDownload(model.definition.id) },
            onResumeClick = { onResumeDownload(model.definition.id) },
            onCancelClick = { onCancelDownload(model.definition.id) },
            onDeleteClick = if (isReady) ({ onDeleteModel(model.definition.id) }) else null,
            downloadButtonLabel = "Download",
            showCompletedStateWhenReady = false,
        )
      }
    }
  }
}

private fun buildMetadata(model: Model): String = buildString {
  append(if (model.definition.source == ModelSource.BUILT_IN) "Built in" else "Downloadable")
  append(" • ")
  append(model.status.name.lowercase().replace('_', ' '))
  val capabilitySummary = model.definition.capabilities.joinToString(", ") { it.toUiLabel() }
  if (capabilitySummary.isNotBlank()) {
    append(" • ")
    append(capabilitySummary)
  }
}

private fun ModelCapability.toUiLabel(): String = name.lowercase()
