/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Configuration screen shown on first launch. User enters the port the Altio service is listening
 * on and a valid bearer token. These are persisted by the caller.
 */
@Composable
fun ConfigScreen(
    port: String,
    token: String,
    onPortChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onConnect: (port: Int, token: String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Connect to Altio Service",
    subtitle: String = "Enter the loopback port and bearer token shown in the main app.",
    errorMessage: String? = null,
    emphasizePortOverride: Boolean = false,
    tokenOptionalInitiallyHidden: Boolean = false,
    connectLabel: String = "Connect",
    resetLabel: String? = null,
    onReset: (() -> Unit)? = null,
) {
  val portInt = port.toIntOrNull()
  val valid = portInt != null && portInt in 1..65535 && token.isNotBlank()
  var showTokenField by
      remember(tokenOptionalInitiallyHidden, token) {
        mutableStateOf(!tokenOptionalInitiallyHidden || token.isBlank())
      }

  var discoveryStatus by remember { mutableStateOf<String?>(null) }
  val context = LocalContext.current

  val attemptDiscovery = {
    runCatching { app.altio.sdk.android.discovery.PortDiscovery.resolvePort(context) }
        .onSuccess { discovered ->
          if (discovered != null && discovered > 0) {
            onPortChange(discovered.toString())
            discoveryStatus = "Auto-detected port: $discovered"
          } else {
            discoveryStatus = "Active service port not found."
          }
        }
        .onFailure { discoveryStatus = "Detection failed." }
  }

  LaunchedEffect(Unit) {
    if (port.isBlank()) {
      attemptDiscovery()
    }
  }

  Column(
      modifier = modifier.fillMaxSize().padding(24.dp),
      verticalArrangement = Arrangement.Center,
  ) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
        subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    errorMessage?.let {
      Spacer(Modifier.height(12.dp))
      Text(
          it,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
      )
    }
    Spacer(Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
      OutlinedTextField(
          value = port,
          onValueChange = { onPortChange(it.filter(Char::isDigit)) },
          label = { Text("Port") },
          placeholder = { Text("e.g. 54231") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.weight(1f),
          supportingText = {
            if (discoveryStatus != null) {
              Text(discoveryStatus!!)
            } else if (emphasizePortOverride) {
              Text("Most connection issues are fixed by updating the port.")
            } else {
              Text("Enter port manually or use Auto-detect")
            }
          },
      )

      OutlinedButton(
          onClick = { attemptDiscovery() },
          modifier = Modifier.padding(top = 8.dp),
      ) {
        Text("Auto-detect")
      }
    }

    Spacer(Modifier.height(12.dp))

    if (showTokenField) {
      OutlinedTextField(
          value = token,
          onValueChange = onTokenChange,
          label = { Text("Bearer Token") },
          placeholder = { Text("Paste token here") },
          visualTransformation = PasswordVisualTransformation(),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
      )
      if (tokenOptionalInitiallyHidden) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { showTokenField = false }, modifier = Modifier.fillMaxWidth()) {
          Text("Keep Saved Token")
        }
      }
    } else {
      OutlinedButton(onClick = { showTokenField = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Edit Bearer Token")
      }
    }

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = { onConnect(portInt!!, token.trim()) },
        enabled = valid,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text(connectLabel)
    }

    if (resetLabel != null && onReset != null) {
      Spacer(Modifier.height(12.dp))
      OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text(resetLabel) }
    }
  }
}
