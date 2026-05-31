/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.altio.sdk.client.AltioAiServiceClient
import app.altio.sdk.contract.job.JobStatus
import app.altio.sdk.contract.job.JobStatusResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Displays jobs tracked by [trackedJobIds]. Polls each job's status every 2 seconds and shows
 * cancel buttons for running/queued jobs.
 *
 * [trackedJobIds] is a shared [SnapshotStateList] populated by ChatScreen and AudioScreen whenever
 * a new job is submitted.
 */
@Composable
fun JobsScreen(
    client: AltioAiServiceClient,
    trackedJobIds: SnapshotStateList<String>,
    modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val jobStatuses = remember { mutableStateListOf<JobStatusResponse>() }
  var cancelling by remember { mutableStateOf<String?>(null) }
  var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
  val shouldUpdateClock = shouldUpdateJobClock(trackedJobIds.toList(), jobStatuses)

  LaunchedEffect(shouldUpdateClock) {
    while (shouldUpdateClock) {
      nowMs = System.currentTimeMillis()
      delay(1_000)
    }
  }

  // Poll job statuses every 2 seconds while any are active
  LaunchedEffect(trackedJobIds.size) {
    while (true) {
      val ids = trackedJobIds.toList()
      if (ids.isEmpty()) {
        delay(2_000)
        continue
      }
      val fresh =
          ids.mapNotNull { id ->
            try {
              client.pollJob(id)
            } catch (e: Exception) {
              Timber.w(e, "Job poll failed for %s", id)
              null
            }
          }
      jobStatuses.clear()
      jobStatuses.addAll(fresh)
      delay(2_000)
    }
  }

  Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("Jobs", style = MaterialTheme.typography.headlineSmall)
      if (trackedJobIds.isNotEmpty()) {
        OutlinedButton(
            onClick = {
              trackedJobIds.removeAll { id ->
                jobStatuses.firstOrNull { it.jobId == id }?.status?.isTerminal() ?: false
              }
            }
        ) {
          Text("Clear done")
        }
      }
    }

    Spacer(Modifier.height(12.dp))

    if (trackedJobIds.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "No jobs yet. Submit a chat or transcription from the other tabs.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      val displayList =
          jobStatuses.ifEmpty {
            trackedJobIds.map { id ->
              JobStatusResponse(
                  jobId = id,
                  sessionId = "",
                  appName = null,
                  status = JobStatus.QUEUED,
                  createdAt = 0L,
              )
            }
          }
      LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(displayList, key = { it.jobId }) { job ->
          JobCard(
              job = job,
              cancelling = cancelling == job.jobId,
              nowMs = nowMs,
              onCancel = {
                cancelling = job.jobId
                scope.launch {
                  try {
                    client.cancelJob(job.jobId)
                  } catch (e: Exception) {
                    Timber.w(e, "Job cancel failed for %s", job.jobId)
                  } finally {
                    cancelling = null
                  }
                }
              },
          )
        }
      }
    }
  }
}

@Composable
private fun JobCard(
    job: JobStatusResponse,
    cancelling: Boolean,
    nowMs: Long,
    onCancel: () -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
              job.jobId,
              style = MaterialTheme.typography.labelMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
          )
          Text(
              "Session: ${job.sessionId.take(16)}…",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          formatJobTimingText(job = job, nowMs = nowMs)?.let { timingText ->
            Text(
                timingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        StatusChip(job.status)
      }

      if (job.status.isCancellable()) {
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onCancel,
            enabled = !cancelling,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
        ) {
          Text(if (cancelling) "Cancelling…" else "Cancel")
        }
      }

      job.output?.let { out ->
        Spacer(Modifier.height(8.dp))
        Text(
            out.take(120) + if (out.length > 120) "…" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      job.errorCode?.let { err ->
        Spacer(Modifier.height(4.dp))
        Text(
            "Error: $err",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
private fun StatusChip(status: JobStatus) {
  val color =
      when (status) {
        JobStatus.QUEUED -> MaterialTheme.colorScheme.tertiary
        JobStatus.RUNNING -> MaterialTheme.colorScheme.primary
        JobStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
        JobStatus.CANCELLED -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.error
      }
  Text(
      status.name.lowercase(),
      style = MaterialTheme.typography.labelSmall,
      color = color,
  )
}

internal fun formatJobTimingText(job: JobStatusResponse, nowMs: Long): String? {
  val createdAt = job.createdAt.takeIf { it > 0L } ?: return null
  val startAt = job.startedAt?.takeIf { it > 0L } ?: createdAt

  return when (job.status) {
    JobStatus.QUEUED -> "Queued for: ${formatJobDuration(nowMs - createdAt)}"
    JobStatus.RUNNING -> "Running for: ${formatJobDuration(nowMs - startAt)}"
    JobStatus.COMPLETED -> {
      val completedAt = job.completedAt?.takeIf { it > 0L } ?: return null
      "Completed in: ${formatJobDuration(completedAt - startAt)}"
    }
    JobStatus.FAILED -> {
      val completedAt = job.completedAt?.takeIf { it > 0L } ?: return null
      "Failed after: ${formatJobDuration(completedAt - startAt)}"
    }
    JobStatus.CANCELLED -> {
      val completedAt = job.completedAt?.takeIf { it > 0L } ?: return null
      "Cancelled after: ${formatJobDuration(completedAt - startAt)}"
    }
  }
}

internal fun shouldUpdateJobClock(
    trackedJobIds: List<String>,
    jobStatuses: List<JobStatusResponse>,
): Boolean {
  if (trackedJobIds.isEmpty()) return false
  val statusesById = jobStatuses.associateBy { it.jobId }
  return trackedJobIds.any { id -> statusesById[id]?.status?.isTerminal() != true }
}

internal fun formatJobDuration(durationMs: Long): String {
  val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
  val hours = totalSeconds / 3_600L
  val minutes = (totalSeconds % 3_600L) / 60L
  val seconds = totalSeconds % 60L

  return if (hours > 0L) {
    "%d:%02d:%02d".format(hours, minutes, seconds)
  } else {
    "%d:%02d".format(minutes, seconds)
  }
}
