/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.ui.model

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.altio.service.domain.model.DownloadProgress
import app.altio.service.domain.model.DownloadStatus
import app.altio.service.domain.model.ModelStatus

@Composable
fun ModelDownloadControls(
    progress: DownloadProgress?,
    modelStatus: ModelStatus,
    onDownloadClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    downloadButtonLabel: String = "Download Model",
    deleteButtonLabel: String = "Delete",
    showCompletedStateWhenReady: Boolean = true,
) {
  var showCancelDialog by remember { mutableStateOf(false) }
  var showDeleteDialog by remember { mutableStateOf(false) }
  val deleteButtonColors =
      ButtonDefaults.outlinedButtonColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer,
      )

  if (showCancelDialog) {
    AlertDialog(
        onDismissRequest = { showCancelDialog = false },
        title = { Text("Cancel Download?") },
        text = {
          Text("This will remove any partial download. You will need to start from scratch.")
        },
        confirmButton = {
          Button(
              onClick = {
                showCancelDialog = false
                onCancelClick()
              }
          ) {
            Text("Cancel Download")
          }
        },
        dismissButton = {
          TextButton(onClick = { showCancelDialog = false }) { Text("Keep Downloading") }
        },
    )
  }

  if (showDeleteDialog) {
    AlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        title = { Text("Delete model?") },
        text = {
          Text(
              "This will remove the downloaded model files. You will need to download the model again to use it."
          )
        },
        confirmButton = {
          Button(
              onClick = {
                showDeleteDialog = false
                onDeleteClick?.invoke()
              },
              colors =
                  ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.error,
                      contentColor = MaterialTheme.colorScheme.onError,
                  ),
          ) {
            Text(deleteButtonLabel)
          }
        },
        dismissButton = {
          TextButton(onClick = { showDeleteDialog = false }) { Text("Keep Model") }
        },
    )
  }

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    val isReady = modelStatus == ModelStatus.READY || modelStatus == ModelStatus.LOADED
    val shouldShowProgress =
        when (progress?.status) {
          DownloadStatus.QUEUED,
          DownloadStatus.DOWNLOADING,
          DownloadStatus.PAUSED,
          DownloadStatus.VERIFYING -> true
          DownloadStatus.SUCCESS -> showCompletedStateWhenReady
          DownloadStatus.FAILED,
          DownloadStatus.CANCELLED,
          null -> false
        }
    val effectiveStatus =
        when {
          progress == null ->
              if (showCompletedStateWhenReady && isReady) DownloadStatus.SUCCESS else null
          progress.status == DownloadStatus.CANCELLED ->
              if (showCompletedStateWhenReady && isReady) DownloadStatus.SUCCESS else null
          else -> progress.status
        }
    val shouldShowDeleteAction =
        onDeleteClick != null &&
            (effectiveStatus == DownloadStatus.SUCCESS || (!showCompletedStateWhenReady && isReady))

    if (progress != null && shouldShowProgress) {
      LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
      Text(
          text = formatProgress(progress),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    when (effectiveStatus) {
      null -> {
        Button(onClick = onDownloadClick, modifier = Modifier.fillMaxWidth()) {
          Text(downloadButtonLabel)
        }
      }
      DownloadStatus.QUEUED,
      DownloadStatus.DOWNLOADING -> {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onPauseClick) { Text("Pause") }
          OutlinedButton(onClick = { showCancelDialog = true }) { Text("Cancel") }
        }
      }
      DownloadStatus.VERIFYING -> {
        Text(
            text = "Verifying download…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      DownloadStatus.PAUSED -> {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(onClick = onResumeClick) { Text("Resume") }
          OutlinedButton(onClick = { showCancelDialog = true }) { Text("Cancel") }
        }
      }
      DownloadStatus.FAILED -> {
        Text(
            text = "Download failed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = progress?.failureReason?.toUiMessage() ?: "An unknown error occurred.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Button(onClick = onDownloadClick) { Text("Retry") }
      }
      DownloadStatus.SUCCESS -> {
        if (showCompletedStateWhenReady) {
          Text(
              text = "Download complete",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.primary,
          )
        }
      }
      else -> {
        Button(onClick = onDownloadClick, modifier = Modifier.fillMaxWidth()) {
          Text(downloadButtonLabel)
        }
      }
    }

    if (shouldShowDeleteAction) {
      OutlinedButton(
          onClick = { showDeleteDialog = true },
          colors = deleteButtonColors,
      ) {
        Text(deleteButtonLabel)
      }
    }
  }
}
