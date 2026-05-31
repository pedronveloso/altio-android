/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.altio.service.domain.job.JobStatus
import app.altio.service.domain.job.JobType
import java.time.Duration
import java.time.Instant

@Immutable
data class SessionManagementState(
    val sessions: List<ManagedSessionItem>,
    val activeJobs: List<ManagedJobItem>,
)

@Immutable
data class ManagedSessionItem(
    val sessionId: String,
    val appName: String?,
    val modelId: String,
    val messageCount: Int,
    val createdAt: Instant,
    val lastActiveAt: Instant,
)

@Immutable
data class ManagedJobItem(
    val jobId: String,
    val sessionId: String,
    val appName: String?,
    val type: JobType,
    val status: JobStatus,
    val createdAt: Instant,
)

@Composable
fun SessionManagementScreen(
    state: SessionManagementState,
    onCancelSession: (String) -> Unit,
    onCancelJob: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  LazyColumn(
      modifier = modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Text("Sessions & Jobs", style = MaterialTheme.typography.headlineSmall)
      Spacer(Modifier.height(4.dp))
      Text(
          "Review active sessions and cancel sessions or jobs by requesting app.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    item {
      SectionHeader(
          title = "Sessions",
          count = state.sessions.size,
          emptyMessage = "No active sessions.",
      )
    }

    if (state.sessions.isEmpty()) {
      item { EmptyCard("No active sessions are registered right now.") }
    } else {
      items(state.sessions, key = { it.sessionId }) { session ->
        SessionCard(session = session, onCancel = { onCancelSession(session.sessionId) })
      }
    }

    item {
      Spacer(Modifier.height(8.dp))
      SectionHeader(
          title = "Active Jobs",
          count = state.activeJobs.size,
          emptyMessage = "No queued or running jobs.",
      )
    }

    if (state.activeJobs.isEmpty()) {
      item { EmptyCard("No queued or running jobs need attention.") }
    } else {
      items(state.activeJobs, key = { it.jobId }) { job ->
        JobCard(job = job, onCancel = { onCancelJob(job.jobId) })
      }
    }
  }
}

@Composable
private fun SectionHeader(title: String, count: Int, emptyMessage: String) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(
        if (count == 0) emptyMessage else "$count item${if (count == 1) "" else "s"}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun SessionCard(session: ManagedSessionItem, onCancel: () -> Unit) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(session.appName ?: "Unknown app", style = MaterialTheme.typography.titleSmall)
          Text(
              "Session ${session.sessionId.take(12)}…",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
              "Model ${session.modelId}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text("${session.messageCount} msg") },
        )
      }

      Text(
          "Created ${formatRelativeTime(session.createdAt)}",
          style = MaterialTheme.typography.bodyMedium,
      )
      Text(
          "Last used ${formatRelativeTime(session.lastActiveAt)}",
          style = MaterialTheme.typography.bodyMedium,
      )

      OutlinedButton(onClick = onCancel) { Text("Cancel Session") }
    }
  }
}

@Composable
private fun JobCard(job: ManagedJobItem, onCancel: () -> Unit) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(job.appName ?: "Unknown app", style = MaterialTheme.typography.titleSmall)
          Text(
              "${job.type.name.lowercase().replaceFirstChar(Char::titlecase)} job",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
              "Job ${job.jobId.take(12)}…",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
              "Session ${job.sessionId.take(12)}…",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        StatusChip(job.status)
      }

      Text(
          "Created ${formatRelativeTime(job.createdAt)}",
          style = MaterialTheme.typography.bodyMedium,
      )

      OutlinedButton(
          onClick = onCancel,
          enabled = job.status.isCancellable(),
      ) {
        Text("Cancel Job")
      }
    }
  }
}

@Composable
private fun StatusChip(status: JobStatus) {
  val color =
      when (status) {
        JobStatus.QUEUED -> MaterialTheme.colorScheme.secondary
        JobStatus.RUNNING -> MaterialTheme.colorScheme.primary
        JobStatus.COMPLETED -> Color(0xFF2E7D32)
        JobStatus.FAILED -> MaterialTheme.colorScheme.error
        JobStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
      }
  AssistChip(
      onClick = {},
      enabled = false,
      label = { Text(status.name.lowercase().replaceFirstChar(Char::titlecase)) },
      border = null,
      colors =
          androidx.compose.material3.AssistChipDefaults.assistChipColors(
              disabledContainerColor = color.copy(alpha = 0.14f),
              disabledLabelColor = color,
          ),
  )
}

@Composable
private fun EmptyCard(message: String) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Text(
        message,
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

internal fun formatRelativeTime(timestamp: Instant, now: Instant = Instant.now()): String {
  val seconds = Duration.between(timestamp, now).seconds.coerceAtLeast(0)
  return when {
    seconds < 10 -> "just now"
    seconds < 60 -> "${seconds}s ago"
    seconds < 3_600 -> "${seconds / 60}m ago"
    seconds < 86_400 -> "${seconds / 3_600}h ago"
    seconds < 604_800 -> "${seconds / 86_400}d ago"
    else -> "${seconds / 604_800}w ago"
  }
}
