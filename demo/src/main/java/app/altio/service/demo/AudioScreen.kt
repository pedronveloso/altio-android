/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.demo

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.altio.sdk.client.AltioAiServiceClient
import app.altio.sdk.client.NotFoundException
import app.altio.sdk.contract.error.ApiErrorCode
import app.altio.sdk.contract.job.JobStatus
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Stable
internal class AudioScreenState
private constructor(
    private val client: AltioAiServiceClient,
    private val sessionId: String,
    private val trackedJobIds: SnapshotStateList<String>,
    private val onSessionInvalid: suspend () -> String,
    private val scope: CoroutineScope,
    private val context: Context,
) {
  val recorder = AacRecorder(context)

  var selectedAudio by mutableStateOf<SelectedAudioSource?>(null)
  var statusText by mutableStateOf("Record audio or pick a file to transcribe.")
  var transcript by mutableStateOf<String?>(null)
  var busy by mutableStateOf(false)
  var isRecording by mutableStateOf(false)
  var recordingStartTimeMs by mutableLongStateOf(0L)

  fun startRecording(file: File) {
    runCatching { recorder.start(file) }
        .onSuccess {
          selectedAudio?.deleteIfTemporary()
          selectedAudio = null
          transcript = null
          isRecording = true
          recordingStartTimeMs = System.currentTimeMillis()
          statusText = "Recording AAC audio…"
        }
        .onFailure { error -> statusText = error.message ?: "Unable to start recording." }
  }

  fun stopRecording() {
    runCatching { recorder.stop() }
        .onSuccess { file ->
          selectedAudio?.deleteIfTemporary()
          selectedAudio =
              RecordedAudioSource(
                  file = file,
                  fileName = "recording-${System.currentTimeMillis()}.m4a",
              )
          isRecording = false
          statusText =
              "Recording saved (${file.length().formatSize()}). Tap 'Transcribe' to upload."
        }
        .onFailure { error ->
          isRecording = false
          statusText = error.message ?: "Failed to stop recording."
        }
  }

  fun clearSelection() {
    selectedAudio?.deleteIfTemporary()
    selectedAudio = null
    transcript = null
    statusText = "Selection cleared."
  }

  fun selectAudio(source: SelectedAudioSource) {
    selectedAudio?.deleteIfTemporary()
    selectedAudio = source
    statusText = "Selected ${source.fileName}. Tap 'Transcribe' to upload."
    transcript = null
  }

  fun transcribe() {
    val source = selectedAudio ?: return
    busy = true
    transcript = null
    statusText = "Reading audio…"
    scope.launch {
      try {
        val bytes = source.readBytes(context)
        statusText = "Uploading (${bytes.size.toLong().formatSize()})…"
        val job =
            runCatching {
                  client.transcribe(
                      sessionId = sessionId,
                      audioBytes = bytes,
                      mimeType = source.mimeType,
                      fileName = source.fileName,
                  )
                }
                .recoverCatching { error ->
                  if (!error.isInvalidSession()) throw error
                  client.transcribe(
                      sessionId = onSessionInvalid(),
                      audioBytes = bytes,
                      mimeType = source.mimeType,
                      fileName = source.fileName,
                  )
                }
                .getOrThrow()
        trackedJobIds.add(job.jobId)

        statusText = "Transcribing… (job ${job.jobId})"
        val result = pollUntilComplete(client, job.jobId) { msg -> statusText = msg }

        transcript = result
        statusText = if (result != null) "Done." else "Transcription failed."
      } catch (e: Exception) {
        statusText = "Error: ${e.message}"
      } finally {
        busy = false
      }
    }
  }

  fun cleanup() {
    recorder.cancel()
    selectedAudio?.deleteIfTemporary()
  }

  companion object {
    fun create(
        client: AltioAiServiceClient,
        sessionId: String,
        trackedJobIds: SnapshotStateList<String>,
        onSessionInvalid: suspend () -> String,
        scope: CoroutineScope,
        context: Context,
    ): AudioScreenState =
        AudioScreenState(
            client = client,
            sessionId = sessionId,
            trackedJobIds = trackedJobIds,
            onSessionInvalid = onSessionInvalid,
            scope = scope,
            context = context,
        )
  }
}

@Composable
internal fun rememberAudioScreenState(
    client: AltioAiServiceClient,
    sessionId: String,
    trackedJobIds: SnapshotStateList<String>,
    onSessionInvalid: suspend () -> String,
): AudioScreenState {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current.applicationContext
  return remember(client, sessionId, trackedJobIds, onSessionInvalid, scope) {
    AudioScreenState.create(
        client = client,
        sessionId = sessionId,
        trackedJobIds = trackedJobIds,
        onSessionInvalid = onSessionInvalid,
        scope = scope,
        context = context,
    )
  }
}

/**
 * Lets the user record AAC audio or pick any audio file, upload it to the AI service for
 * transcription, and display the resulting transcript once the job completes.
 *
 * Recordings are captured via [AacRecorder] (64 kbps mono AAC, smaller files over the wire). The
 * service converts any supported format to WAV PCM-16-bit before running inference.
 */
@Composable
internal fun AudioScreen(
    audioState: AudioScreenState,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current

  val elapsedRecordingMs by
      produceState(initialValue = 0L, audioState.isRecording, audioState.recordingStartTimeMs) {
        if (!audioState.isRecording) {
          value = 0L
          return@produceState
        }
        while (isActive) {
          value = System.currentTimeMillis() - audioState.recordingStartTimeMs
          delay(250L)
        }
      }

  val filePicker =
      rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val (fileName, mimeType, sizeBytes) = context.resolveAudioInfo(uri)
        audioState.selectAudio(
            PickedAudioSource(
                uri = uri,
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
            )
        )
      }

  val audioPermissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
          audioState.statusText = "Microphone permission denied."
          return@rememberLauncherForActivityResult
        }
        val file = File(context.cacheDir, "audio/recording-${UUID.randomUUID()}.m4a")
        audioState.startRecording(file)
      }

  Column(
      modifier = modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Audio Transcription", style = MaterialTheme.typography.headlineSmall)

    Button(
        onClick = { filePicker.launch("audio/*") },
        enabled = !audioState.busy && !audioState.isRecording,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text(if (audioState.selectedAudio == null) "Pick audio file" else "Change file")
    }

    Row(modifier = Modifier.fillMaxWidth()) {
      Button(
          onClick = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
          enabled = !audioState.busy && !audioState.isRecording,
          modifier = Modifier.weight(1f),
      ) {
        Text("Start recording")
      }
      Spacer(Modifier.width(12.dp))
      Button(
          onClick = { audioState.stopRecording() },
          enabled = audioState.isRecording,
          modifier = Modifier.weight(1f),
      ) {
        Text("Stop recording")
      }
    }

    Text(audioState.statusText, style = MaterialTheme.typography.bodySmall)

    if (audioState.isRecording) {
      Text(
          "Recording: ${formatElapsed(elapsedRecordingMs)}",
          style = MaterialTheme.typography.bodyMedium,
      )
    }

    audioState.selectedAudio?.let { source ->
      OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text("Selected audio", style = MaterialTheme.typography.titleMedium)
          Text(source.fileName, style = MaterialTheme.typography.bodyMedium)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(source.mimeType, style = MaterialTheme.typography.bodySmall)
            Text(source.sizeBytes.formatSize(), style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }

    Button(
        onClick = { audioState.transcribe() },
        enabled = audioState.selectedAudio != null && !audioState.busy && !audioState.isRecording,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Transcribe")
    }

    Button(
        onClick = { audioState.clearSelection() },
        enabled = audioState.selectedAudio != null && !audioState.busy && !audioState.isRecording,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Clear selection")
    }

    if (audioState.busy) {
      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    }

    val text = audioState.transcript
    if (text != null) {
      Spacer(Modifier.height(8.dp))
      Text("Transcript", style = MaterialTheme.typography.titleMedium)
      OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
  }
}

private suspend fun pollUntilComplete(
    client: AltioAiServiceClient,
    jobId: String,
    onStatus: (String) -> Unit,
): String? {
  var attempt = 0
  while (true) {
    delay(POLL_INTERVAL_MS)
    attempt++
    val job = client.pollJob(jobId)
    onStatus("Transcribing… (${job.status.name.lowercase()}, poll #$attempt)")
    when (job.status) {
      JobStatus.COMPLETED -> return job.output
      JobStatus.FAILED,
      JobStatus.CANCELLED -> return null
      else -> Unit
    }
  }
}

internal sealed interface SelectedAudioSource {
  val fileName: String
  val mimeType: String
  val sizeBytes: Long

  suspend fun readBytes(context: Context): ByteArray

  fun deleteIfTemporary() = Unit
}

internal data class PickedAudioSource(
    val uri: Uri,
    override val fileName: String,
    override val mimeType: String,
    override val sizeBytes: Long,
) : SelectedAudioSource {
  override suspend fun readBytes(context: Context): ByteArray =
      context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: error("Failed to read selected audio.")
}

internal class RecordedAudioSource(
    private val file: File,
    override val fileName: String,
) : SelectedAudioSource {
  override val mimeType: String = AacRecorder.MIME_TYPE
  override val sizeBytes: Long
    get() = file.length()

  override suspend fun readBytes(context: Context): ByteArray = file.readBytes()

  override fun deleteIfTemporary() {
    file.delete()
  }
}

private data class AudioInfo(val fileName: String, val mimeType: String, val sizeBytes: Long)

private fun Context.resolveAudioInfo(uri: Uri): AudioInfo {
  var fileName = "audio"
  var sizeBytes = -1L
  contentResolver
      .query(
          uri,
          arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
          null,
          null,
          null,
      )
      ?.use { cursor ->
        if (cursor.moveToFirst()) {
          fileName = cursor.getString(0) ?: fileName
          sizeBytes = cursor.getLong(1)
        }
      }
  val mimeType = contentResolver.getType(uri) ?: "audio/mpeg"
  return AudioInfo(fileName = fileName, mimeType = mimeType, sizeBytes = sizeBytes)
}

private fun Long.formatSize(): String =
    when {
      this < 0 -> "unknown size"
      this < 1024 -> "$this B"
      this < 1024 * 1024 -> "${this / 1024} KB"
      else -> "${"%.1f".format(this / (1024.0 * 1024.0))} MB"
    }

private fun formatElapsed(durationMs: Long): String {
  val totalSeconds = durationMs / 1000L
  val minutes = totalSeconds / 60L
  val seconds = totalSeconds % 60L
  return "%d:%02d".format(minutes, seconds)
}

private const val POLL_INTERVAL_MS = 1_500L

private fun Throwable.isInvalidSession(): Boolean =
    this is NotFoundException && apiError?.code == ApiErrorCode.SESSION_NOT_FOUND
