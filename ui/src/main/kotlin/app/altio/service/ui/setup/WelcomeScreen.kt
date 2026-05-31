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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    versionName: String,
    modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()

  Box(modifier = modifier.fillMaxSize().padding(32.dp)) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
      Text(
          text = "Altio Service",
          style = MaterialTheme.typography.displaySmall,
          textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(12.dp))
      Text(
          text = "On-device AI, privately yours",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(24.dp))
      Text(
          text =
              "Runs a local HTTP server that exposes inference, transcription, and session " +
                  "management — all without sending data to the cloud.",
          style = MaterialTheme.typography.bodyMedium,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(48.dp))
      Button(
          onClick = onGetStarted,
          modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Get Started")
      }
    }
    Text(
        text = "v$versionName",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.BottomCenter),
    )
  }
}
