/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.ui.token

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.altio.service.domain.token.ClientToken
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Immutable data class TokenManagerTokens(val items: List<ClientToken>)

/**
 * Lets the server operator manage bearer tokens: generate new ones (displayed once in a dialog),
 * and revoke existing ones.
 *
 * @param tokens List of active (non-revoked) tokens, typically collected from
 *   [TokenRepository.observeTokens].
 * @param onGenerate Called with a label when the user taps "Generate". The implementation should
 *   call [TokenRepository.generateToken] and return the raw token string, which is shown to the
 *   user exactly once.
 * @param onRevoke Called with the [ClientToken.tokenHash] of the token to revoke.
 */
@Composable
fun TokenManagerScreen(
    tokens: TokenManagerTokens,
    onGenerate: suspend (label: String) -> String,
    onRevoke: suspend (tokenHash: String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  var showGenerateDialog by remember { mutableStateOf(false) }
  var generatedToken by remember { mutableStateOf<String?>(null) }
  var labelInput by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.widthIn(max = 600.dp).fillMaxHeight().padding(16.dp)) {
      Text(
          "Tokens grant access to all API endpoints. Share each token with exactly one client app.",
          style = MaterialTheme.typography.bodySmall,
      )
      Spacer(Modifier.height(16.dp))

      Button(
          onClick = { showGenerateDialog = true },
          modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Generate New Token")
      }

      Spacer(Modifier.height(16.dp))

      if (tokens.items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("No active tokens.", style = MaterialTheme.typography.bodyMedium)
        }
      } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(tokens.items, key = { it.tokenHash }) { token ->
            TokenCard(
                token = token,
                onRevoke = {
                  scope.launch {
                    busy = true
                    onRevoke(token.tokenHash)
                    busy = false
                  }
                },
                enabled = !busy,
            )
          }
        }
      }
    }
  } // Box

  // Generate dialog — asks for a label then shows the raw token once.
  if (showGenerateDialog) {
    AlertDialog(
        onDismissRequest = {
          showGenerateDialog = false
          labelInput = ""
        },
        title = { Text("New Token") },
        text = {
          Column {
            Text("Enter a label so you can identify which app uses this token.")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = labelInput,
                onValueChange = { labelInput = it },
                label = { Text("Label (e.g. \"My Demo App\")") },
                singleLine = true,
            )
          }
        },
        confirmButton = {
          Button(
              onClick = {
                scope.launch {
                  busy = true
                  val raw = onGenerate(labelInput.ifBlank { "Unnamed" })
                  generatedToken = raw
                  showGenerateDialog = false
                  labelInput = ""
                  busy = false
                }
              },
              enabled = !busy,
          ) {
            Text("Generate")
          }
        },
        dismissButton = {
          TextButton(
              onClick = {
                showGenerateDialog = false
                labelInput = ""
              }
          ) {
            Text("Cancel")
          }
        },
    )
  }

  // Show the raw token once — user must copy it before dismissing.
  val rawToken = generatedToken
  if (rawToken != null) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { generatedToken = null },
        title = { Text("Copy Your Token") },
        text = {
          Column {
            Text(
                "This token will not be shown again. Copy it now and paste it into your client app.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            SelectionContainer {
              Text(
                  text = rawToken,
                  style =
                      MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
              )
            }
            OutlinedButton(
                onClick = {
                  clipboardManager.setText(AnnotatedString(rawToken))
                  copied = true
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
              Icon(Icons.Default.ContentCopy, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text("Copy")
            }
            if (copied) {
              LaunchedEffect(Unit) {
                delay(2_000)
                copied = false
              }
              Text(
                  "Copied to clipboard",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.padding(top = 4.dp),
              )
            }
          }
        },
        confirmButton = { Button(onClick = { generatedToken = null }) { Text("Done") } },
    )
  }
}

@Composable
private fun TokenCard(
    token: ClientToken,
    onRevoke: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
  Card(modifier = modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
              token.label,
              style = MaterialTheme.typography.titleSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
          )
          Text(
              "ID: ${token.clientId}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        OutlinedButton(
            onClick = onRevoke,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "Revoke ${token.label}" },
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
        ) {
          Text("Revoke")
        }
      }
      Spacer(Modifier.height(4.dp))
      Text(
          "Created: ${formatTimestamp(token.createdAt)}",
          style = MaterialTheme.typography.bodySmall,
      )
      val lastUsed = token.lastUsedAt
      if (lastUsed != null) {
        Text(
            "Last used: ${formatTimestamp(lastUsed)}",
            style = MaterialTheme.typography.bodySmall,
        )
      } else {
        Text(
            "Never used",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

private val tokenTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

private fun formatTimestamp(timestampMs: Long): String =
    tokenTimeFormatter.format(Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()))
