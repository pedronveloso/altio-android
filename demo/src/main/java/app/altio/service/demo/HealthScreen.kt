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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.altio.sdk.client.AltioAiServiceClient
import app.altio.sdk.contract.health.DiagnosticsResponse
import app.altio.sdk.contract.health.HealthResponse
import kotlinx.coroutines.launch

@Composable
fun HealthScreen(
    client: AltioAiServiceClient,
    modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  var health by remember { mutableStateOf<HealthResponse?>(null) }
  var diagnostics by remember { mutableStateOf<DiagnosticsResponse?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var loading by remember { mutableStateOf(false) }

  fun refresh() {
    loading = true
    error = null
    scope.launch {
      try {
        health = client.health()
        diagnostics = client.diagnostics()
      } catch (e: Exception) {
        error = e.message
      } finally {
        loading = false
      }
    }
  }

  LaunchedEffect(Unit) { refresh() }

  Column(
      modifier = modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("Health & Diagnostics", style = MaterialTheme.typography.headlineSmall)
      Button(onClick = ::refresh, enabled = !loading) { Text(if (loading) "…" else "Refresh") }
    }

    error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }

    health?.let { h ->
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text("Health", style = MaterialTheme.typography.titleSmall)
          Spacer(Modifier.height(8.dp))
          DiagRow("status", h.status)
        }
      }
    }

    diagnostics?.let { d ->
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text("Diagnostics", style = MaterialTheme.typography.titleSmall)
          Spacer(Modifier.height(8.dp))
          DiagRow("uptime_seconds", d.uptimeSeconds.toString())
          HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
          DiagRow("active_sessions", d.activeSessions.toString())
          HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
          DiagRow("active_jobs", d.activeJobs.toString())
        }
      }
    }
  }
}

@Composable
private fun DiagRow(label: String, value: String) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Text(
        value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
