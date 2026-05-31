/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Immutable
data class DashboardModelManagementState(
    val actionNeeded: Boolean = false,
    val statusMessage: String? = null,
    val detailMessage: String? = null,
    val attentionCount: Int = 0,
)

@Immutable
data class DashboardServiceStartFailure(
    val code: String,
    val title: String,
    val description: String,
)

data class DashboardState(
    val serverRunning: Boolean,
    val port: Int?,
    val modelId: String?,
    val modelReady: Boolean,
    val modelLoaded: Boolean,
    val modelManagement: DashboardModelManagementState = DashboardModelManagementState(),
    val activeSessions: Int,
    val activeJobs: Int,
    val uptimeSeconds: Long,
    val isModelTestRunning: Boolean = false,
    val modelTestPassed: Boolean = false,
    val modelTestMessage: String? = null,
    val serviceStartFailure: DashboardServiceStartFailure? = null,
)

@Composable
fun DashboardScreen(
    state: DashboardState,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onRunModelTest: () -> Unit,
    onUnloadModel: () -> Unit,
    onNavigateToSessionManager: () -> Unit,
    onNavigateToModelManager: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val clipboard = LocalClipboardManager.current

  Column(
      modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Service Start Failure section if present
    state.serviceStartFailure?.let { failure -> ServiceStartFailureSurface(failure = failure) }

    // 2. Service Control Panel
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (state.serverRunning) {
                      MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    } else {
                      MaterialTheme.colorScheme.surface
                    }
            ),
        border =
            androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
              text = "Service Control",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          val statusText = if (state.serverRunning) "Running" else "Stopped"
          val statusColor =
              if (state.serverRunning) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.error
              }

          Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            StatusDot(running = state.serverRunning)
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = statusColor,
            )
          }
        }

        Spacer(Modifier.height(12.dp))

        if (state.serverRunning) {
          // Running Stats Row
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(
                  text = "Uptime",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                  text = formatUptime(state.uptimeSeconds),
                  style = MaterialTheme.typography.bodyLarge,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                  text = "Sessions",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                  text = state.activeSessions.toString(),
                  style = MaterialTheme.typography.bodyLarge,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Column(horizontalAlignment = Alignment.End) {
              Text(
                  text = "Active Jobs",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                  text = state.activeJobs.toString(),
                  style = MaterialTheme.typography.bodyLarge,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        } else {
          // Off state illustration text
          Text(
              text =
                  "The local inference service is offline. Clients cannot connect until you start it.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Spacer(Modifier.height(16.dp))

        // Toggle Control Action Button
        if (state.serverRunning) {
          OutlinedButton(
              onClick = onStopServer,
              colors =
                  ButtonDefaults.outlinedButtonColors(
                      contentColor = MaterialTheme.colorScheme.error,
                  ),
              border =
                  androidx.compose.foundation.BorderStroke(
                      width = 1.dp,
                      color = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                  ),
              modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Stop", fontWeight = FontWeight.Bold)
          }
        } else {
          Button(
              onClick = onStartServer,
              modifier = Modifier.fillMaxWidth(),
              shape = MaterialTheme.shapes.medium,
          ) {
            Text("Start", fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // 3. Server URL and Connection Info (Only if running)
    if (state.serverRunning && state.port != null) {
      val url = "http://127.0.0.1:${state.port}/v1"
      Card(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
          border =
              androidx.compose.foundation.BorderStroke(
                  width = 1.dp,
                  color = MaterialTheme.colorScheme.outlineVariant,
              ),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
                text = "Connection API Endpoint",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Port display with manual copy action trigger for test verification
            TextButton(
                onClick = { clipboard.setText(AnnotatedString(state.port.toString())) },
                contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
              Icon(
                  imageVector = Icons.Filled.ContentCopy,
                  contentDescription = "Copy port",
                  modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(text = "Port ${state.port}", style = MaterialTheme.typography.labelMedium)
            }
          }

          Spacer(Modifier.height(8.dp))

          // Clickable endpoint display container mimicking a code snippet
          Row(
              modifier =
                  Modifier.fillMaxWidth()
                      .clip(RoundedCornerShape(8.dp))
                      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                      .border(
                          width = 1.dp,
                          color = MaterialTheme.colorScheme.outlineVariant,
                          shape = RoundedCornerShape(8.dp),
                      )
                      .clickable(
                          onClickLabel = "Copy server URL to clipboard",
                      ) {
                        clipboard.setText(AnnotatedString(url))
                      }
                      .padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "Copy server URL",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
          }
        }
      }
    }

    // 4. Model Status & Management Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border =
            androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Inference Model",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        if (state.modelId != null) {
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
                text = state.modelId,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Dynamic Model Loaded Pill
            val modelLoadedBg =
                if (state.modelLoaded) {
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                  MaterialTheme.colorScheme.surfaceVariant
                }
            val modelLoadedTextColor =
                if (state.modelLoaded) {
                  MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                }

            Surface(
                color = modelLoadedBg,
                shape = MaterialTheme.shapes.extraSmall,
                modifier =
                    Modifier.semantics {
                      contentDescription =
                          if (state.modelLoaded) "Model status: Loaded"
                          else "Model status: Not loaded"
                    },
            ) {
              Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                StatusDot(running = state.modelLoaded)
                Text(
                    text = if (state.modelLoaded) "Loaded" else "Not loaded",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = modelLoadedTextColor,
                )
              }
            }
          }
        } else {
          Text(
              text = "No model active",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        // Model Management Required alert callout
        if (state.modelManagement.actionNeeded) {
          Spacer(Modifier.height(12.dp))
          Surface(
              color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
              shape = MaterialTheme.shapes.medium,
              border =
                  androidx.compose.foundation.BorderStroke(
                      width = 1.dp,
                      color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                  ),
              modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                  text = state.modelManagement.statusMessage.orEmpty(),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onErrorContainer,
              )
              state.modelManagement.detailMessage?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                )
              }
              Button(
                  onClick = onNavigateToModelManager,
                  modifier = Modifier.fillMaxWidth(),
                  shape = MaterialTheme.shapes.small,
              ) {
                Text("Manage Models", fontWeight = FontWeight.Bold)
              }
            }
          }
        }

        // Model Testing Dashboard Panel
        if (state.modelReady || state.isModelTestRunning || state.modelTestMessage != null) {
          Spacer(Modifier.height(16.dp))

          Button(
              onClick = onRunModelTest,
              enabled = state.modelReady && !state.isModelTestRunning,
              modifier = Modifier.fillMaxWidth(),
              shape = MaterialTheme.shapes.medium,
          ) {
            if (state.isModelTestRunning) {
              CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.onPrimary,
              )
              Spacer(Modifier.width(8.dp))
              Text("Testing model...")
            } else {
              Text("Test Model")
            }
          }

          state.modelTestMessage?.let { message ->
            Spacer(Modifier.height(10.dp))

            val messageBg =
                if (state.modelTestPassed) {
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                } else {
                  MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                }
            val messageBorder =
                if (state.modelTestPassed) {
                  MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                } else {
                  MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = messageBg,
                shape = MaterialTheme.shapes.small,
                border = androidx.compose.foundation.BorderStroke(1.dp, messageBorder),
            ) {
              Row(
                  modifier = Modifier.padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                if (state.modelTestPassed) {
                  Icon(
                      imageVector = Icons.Filled.CheckCircle,
                      contentDescription = "Model test passed",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(18.dp),
                  )
                } else {
                  StatusDot(running = false, modifier = Modifier.size(8.dp))
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (state.modelTestPassed) {
                          MaterialTheme.colorScheme.primary
                        } else {
                          MaterialTheme.colorScheme.error
                        },
                    fontWeight = FontWeight.Medium,
                )
              }
            }
          }
        }

        // Unload Button action
        val showUnloadButton = state.modelLoaded && !state.isModelTestRunning
        if (showUnloadButton) {
          val canUnload = state.activeSessions == 0
          var showUnloadDialog by remember { mutableStateOf(false) }

          Spacer(Modifier.height(12.dp))
          OutlinedButton(
              onClick = { showUnloadDialog = true },
              enabled = canUnload,
              colors =
                  ButtonDefaults.outlinedButtonColors(
                      contentColor = MaterialTheme.colorScheme.error,
                  ),
              border =
                  androidx.compose.foundation.BorderStroke(
                      width = 1.dp,
                      color =
                          if (canUnload) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                          } else {
                            MaterialTheme.colorScheme.outlineVariant
                          },
                  ),
              modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Unload Model")
          }
          if (!canUnload) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Cannot unload while sessions are active.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          if (showUnloadDialog) {
            AlertDialog(
                onDismissRequest = { showUnloadDialog = false },
                title = { Text("Unload model?") },
                text = {
                  Text(
                      "The model will be removed from memory. It will need to reload before serving the next request."
                  )
                },
                confirmButton = {
                  Button(
                      onClick = {
                        showUnloadDialog = false
                        onUnloadModel()
                      },
                      colors =
                          ButtonDefaults.buttonColors(
                              containerColor = MaterialTheme.colorScheme.error,
                              contentColor = MaterialTheme.colorScheme.onError,
                          ),
                  ) {
                    Text("Unload")
                  }
                },
                dismissButton = {
                  TextButton(onClick = { showUnloadDialog = false }) { Text("Cancel") }
                },
            )
          }
        }
      }
    }

    Spacer(Modifier.height(8.dp))

    // 5. Global Action & Management Console Navigation
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
          onClick = onNavigateToSessionManager,
          modifier = Modifier.fillMaxWidth(),
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.secondaryContainer,
                  contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
              ),
          shape = MaterialTheme.shapes.medium,
      ) {
        Text("Manage Sessions & Jobs", fontWeight = FontWeight.Bold)
      }

      OutlinedButton(
          onClick = onNavigateToSettings,
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
      ) {
        Text("Settings", fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun ServiceStartFailureSurface(failure: DashboardServiceStartFailure) {
  Surface(
      color = MaterialTheme.colorScheme.errorContainer,
      shape = MaterialTheme.shapes.medium,
      border =
          androidx.compose.foundation.BorderStroke(
              width = 1.dp,
              color = MaterialTheme.colorScheme.error,
          ),
      modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
            text = failure.code,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier =
                Modifier.background(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                        shape = MaterialTheme.shapes.extraSmall,
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Text(
            text = failure.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
      }
      Text(
          text = failure.description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onErrorContainer,
      )
    }
  }
}

@Composable
private fun StatusDot(running: Boolean, modifier: Modifier = Modifier) {
  Surface(
      modifier =
          modifier.size(10.dp).semantics {
            contentDescription = if (running) "Status: running" else "Status: stopped"
          },
      shape = MaterialTheme.shapes.extraSmall,
      color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
      content = {},
  )
}

private fun formatUptime(seconds: Long): String {
  val h = seconds / 3600
  val m = (seconds % 3600) / 60
  val s = seconds % 60
  return "%02d:%02d:%02d".format(h, m, s)
}
