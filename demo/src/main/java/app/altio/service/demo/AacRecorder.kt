/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.demo

import android.content.Context
import android.media.MediaRecorder
import java.io.File

/** Records mono AAC audio (M4A container) via [MediaRecorder]. The server converts it to WAV. */
class AacRecorder(private val context: Context) {

  private var recorder: MediaRecorder? = null
  private var activeOutputFile: File? = null

  fun start(outputFile: File) {
    check(recorder == null) { "Recording already in progress" }
    outputFile.parentFile?.mkdirs()
    val mediaRecorder =
        MediaRecorder(context).apply {
          setAudioSource(MediaRecorder.AudioSource.MIC)
          setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
          setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
          setAudioChannels(1)
          setAudioSamplingRate(SAMPLE_RATE_HZ)
          setAudioEncodingBitRate(BIT_RATE)
          setOutputFile(outputFile.absolutePath)
          prepare()
          start()
        }
    recorder = mediaRecorder
    activeOutputFile = outputFile
  }

  fun stop(): File {
    val mediaRecorder = recorder ?: error("No recording in progress")
    val outputFile = activeOutputFile ?: error("Missing output file")
    return try {
      mediaRecorder.stop()
      outputFile
    } catch (e: RuntimeException) {
      outputFile.delete()
      throw IllegalStateException("Recording failed before enough audio was captured.", e)
    } finally {
      mediaRecorder.release()
      recorder = null
      activeOutputFile = null
    }
  }

  fun cancel() {
    val mediaRecorder = recorder ?: return
    val outputFile = activeOutputFile
    runCatching { mediaRecorder.stop() }
    mediaRecorder.release()
    recorder = null
    activeOutputFile = null
    outputFile?.delete()
  }

  companion object {
    const val SAMPLE_RATE_HZ = 16_000
    const val MIME_TYPE = "audio/mp4"
    private const val BIT_RATE = 64_000
  }
}
