/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.logging.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.altio.service.domain.debug.LogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Immutable data class DebugLogEntries(val items: List<LogEntry>)

private val logTimeFormatter =
    ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

@Composable
fun DebugLogsScreen(
    entries: DebugLogEntries,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    versionName: String,
    onTestCrash: (() -> Unit)?,
    onClearLogs: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
  val filtered =
      remember(entries, searchQuery) {
        val base =
            if (searchQuery.isBlank()) entries.items
            else
                entries.items.filter { entry ->
                  entry.message.contains(searchQuery, ignoreCase = true) ||
                      (entry.tag?.contains(searchQuery, ignoreCase = true) == true)
                }
        base.reversed()
      }

  Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
    // 1. Search Toolbar
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = { Text("Search logs\u2026") },
        leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = "Search") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    )

    Spacer(Modifier.height(12.dp))

    // 2. Metadata & Global Controls Row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
          color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
          shape = MaterialTheme.shapes.extraSmall,
      ) {
        Text(
            text = "v$versionName",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
      }

      Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        if (onClearLogs != null) {
          OutlinedButton(onClick = onClearLogs, shape = MaterialTheme.shapes.medium) {
            Text("Clear Logs", fontWeight = FontWeight.Bold)
          }
        }
        if (onTestCrash != null) {
          OutlinedButton(
              onClick = onTestCrash,
              colors =
                  ButtonDefaults.outlinedButtonColors(
                      contentColor = MaterialTheme.colorScheme.error
                  ),
              border =
                  androidx.compose.foundation.BorderStroke(
                      width = 1.dp,
                      color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                  ),
              shape = MaterialTheme.shapes.medium,
          ) {
            Text("Test Crash", fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    Spacer(Modifier.height(16.dp))

    // 3. Immersive Console Window
    Surface(
        modifier = Modifier.fillMaxWidth().weight(1f),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.large,
        border =
            androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
      if (filtered.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
              text = "No logs recorded.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
          items(filtered) { entry -> LogEntryRow(entry = entry) }
        }
      }
    }
  }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
  val time =
      remember(entry.timestamp) {
        val formatter = logTimeFormatter.get() ?: SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        formatter.format(Date(entry.timestamp))
      }
  val levelChar =
      remember(entry.priority) {
        when (entry.priority) {
          2 -> "V"
          3 -> "D"
          4 -> "I"
          5 -> "W"
          6 -> "E"
          else -> "?"
        }
      }
  val priorityColor = colorForPriority(entry.priority, MaterialTheme.colorScheme)

  SelectionContainer {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
      // 1. Log Level Badge Column
      Surface(
          color = priorityColor.copy(alpha = 0.12f),
          shape = RoundedCornerShape(4.dp),
          modifier = Modifier.size(width = 18.dp, height = 18.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
              text = levelChar,
              style =
                  MaterialTheme.typography.labelSmall.copy(
                      fontFamily = FontFamily.Monospace,
                      fontWeight = FontWeight.Bold,
                  ),
              color = priorityColor,
          )
        }
      }

      // 2. Timestamp Column
      Text(
          text = time,
          style =
              MaterialTheme.typography.bodySmall.copy(
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Light,
              ),
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
          modifier = Modifier.width(82.dp),
      )

      // 3. Log Component & Message Column
      Column(modifier = Modifier.weight(1f)) {
        entry.tag?.let { tag ->
          Text(
              text = tag,
              style =
                  MaterialTheme.typography.labelSmall.copy(
                      fontFamily = FontFamily.Monospace,
                      fontWeight = FontWeight.Bold,
                  ),
              color = priorityColor.copy(alpha = 0.85f),
              modifier = Modifier.padding(bottom = 2.dp),
          )
        }
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color =
                if (entry.priority == 2) {
                  MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                } else {
                  MaterialTheme.colorScheme.onSurface
                },
        )
      }
    }
  }
}

private fun colorForPriority(
    priority: Int,
    colorScheme: androidx.compose.material3.ColorScheme,
): Color =
    when (priority) {
      2 -> colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
      3 -> colorScheme.onSurface
      4 -> colorScheme.primary
      5 -> Color(0xFFFFA000)
      6 -> colorScheme.error
      else -> colorScheme.onSurface
    }
