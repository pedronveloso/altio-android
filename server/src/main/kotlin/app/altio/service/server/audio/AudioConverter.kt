/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

private const val CODEC_TIMEOUT_US = 10_000L // 10 ms

/**
 * Decodes any Android-supported compressed audio (AAC, M4A, MP3, OGG, FLAC, WAV …) to a WAV file
 * containing 16-bit PCM mono samples, which is the format required by LiteRT-LM's
 * Content.AudioBytes().
 *
 * Uses [MediaExtractor] + [MediaCodec] — no additional dependencies beyond the Android framework.
 *
 * @throws IllegalArgumentException if no audio track is found or the format cannot be decoded.
 */
fun decodeToWav(audioBytes: ByteArray): ByteArray {
  val extractor = MediaExtractor()
  try {
    extractor.setDataSource(ByteArrayMediaDataSource(audioBytes))

    val trackIndex =
        (0 until extractor.trackCount).firstOrNull { i ->
          extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IllegalArgumentException("No audio track found in the provided bytes")

    extractor.selectTrack(trackIndex)
    val format = extractor.getTrackFormat(trackIndex)
    val mime = format.getString(MediaFormat.KEY_MIME)!!
    val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    val channelCount =
        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        else 1

    val rawPcm =
        if (mime == "audio/raw") {
          readRawSamples(extractor)
        } else {
          decodeWithCodec(extractor, format, mime)
        }

    val monoPcm = if (channelCount > 1) downmixToMono(rawPcm, channelCount) else rawPcm
    return buildWavHeader(monoPcm, sampleRate) + monoPcm
  } finally {
    extractor.release()
  }
}

/** Reads raw PCM samples directly from the extractor (used when the container is already PCM). */
private fun readRawSamples(extractor: MediaExtractor): ByteArray {
  val out = ByteArrayOutputStream()
  val chunk = ByteBuffer.allocate(64 * 1024)
  while (true) {
    val read = extractor.readSampleData(chunk, 0)
    if (read < 0) break
    val bytes = ByteArray(read)
    chunk.get(bytes, 0, read)
    out.write(bytes)
    chunk.clear()
    extractor.advance()
  }
  return out.toByteArray()
}

/** Runs a synchronous [MediaCodec] decode loop and returns the concatenated PCM output. */
private fun decodeWithCodec(
    extractor: MediaExtractor,
    format: MediaFormat,
    mime: String,
): ByteArray {
  val decoder = MediaCodec.createDecoderByType(mime)
  try {
    decoder.configure(format, null, null, 0)
    decoder.start()

    val out = ByteArrayOutputStream()
    val info = MediaCodec.BufferInfo()
    var inputDone = false
    var outputDone = false

    while (!outputDone) {
      if (!inputDone) {
        val inputIdx = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
        if (inputIdx >= 0) {
          val inputBuffer = decoder.getInputBuffer(inputIdx)!!
          val size = extractor.readSampleData(inputBuffer, 0)
          if (size < 0) {
            decoder.queueInputBuffer(inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputDone = true
          } else {
            decoder.queueInputBuffer(inputIdx, 0, size, extractor.sampleTime, 0)
            extractor.advance()
          }
        }
      }

      when (val outputIdx = decoder.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED,
        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
        else -> {
          if (outputIdx >= 0) {
            val buf = decoder.getOutputBuffer(outputIdx)!!
            val bytes = ByteArray(info.size)
            buf.get(bytes)
            out.write(bytes)
            decoder.releaseOutputBuffer(outputIdx, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
              outputDone = true
            }
          }
        }
      }
    }

    return out.toByteArray()
  } finally {
    decoder.stop()
    decoder.release()
  }
}

/** Averages all channels into a single mono channel (16-bit little-endian samples). */
private fun downmixToMono(pcm: ByteArray, channelCount: Int): ByteArray {
  val frameCount = pcm.size / (channelCount * 2)
  val mono = ByteArray(frameCount * 2)
  for (i in 0 until frameCount) {
    var sum = 0
    for (ch in 0 until channelCount) {
      val offset = (i * channelCount + ch) * 2
      val sample = ((pcm[offset + 1].toInt() shl 8) or (pcm[offset].toInt() and 0xff)).toShort()
      sum += sample.toInt()
    }
    val mono16 = (sum / channelCount).toShort()
    mono[i * 2] = (mono16.toInt() and 0xff).toByte()
    mono[i * 2 + 1] = ((mono16.toInt() shr 8) and 0xff).toByte()
  }
  return mono
}

/** Builds a 44-byte RIFF/WAVE header for 16-bit PCM mono audio. */
private fun buildWavHeader(pcmBytes: ByteArray, sampleRate: Int): ByteArray {
  val channels = 1
  val bitsPerSample = 16
  val byteRate = sampleRate * channels * bitsPerSample / 8
  val blockAlign = channels * bitsPerSample / 8
  val dataSize = pcmBytes.size
  val header = ByteArray(44)

  header[0] = 'R'.code.toByte()
  header[1] = 'I'.code.toByte()
  header[2] = 'F'.code.toByte()
  header[3] = 'F'.code.toByte()
  val chunkSize = dataSize + 36
  header[4] = (chunkSize and 0xff).toByte()
  header[5] = ((chunkSize shr 8) and 0xff).toByte()
  header[6] = ((chunkSize shr 16) and 0xff).toByte()
  header[7] = ((chunkSize shr 24) and 0xff).toByte()
  header[8] = 'W'.code.toByte()
  header[9] = 'A'.code.toByte()
  header[10] = 'V'.code.toByte()
  header[11] = 'E'.code.toByte()

  header[12] = 'f'.code.toByte()
  header[13] = 'm'.code.toByte()
  header[14] = 't'.code.toByte()
  header[15] = ' '.code.toByte()
  header[16] = 16
  header[17] = 0
  header[18] = 0
  header[19] = 0
  header[20] = 1
  header[21] = 0
  header[22] = channels.toByte()
  header[23] = 0
  header[24] = (sampleRate and 0xff).toByte()
  header[25] = ((sampleRate shr 8) and 0xff).toByte()
  header[26] = ((sampleRate shr 16) and 0xff).toByte()
  header[27] = ((sampleRate shr 24) and 0xff).toByte()
  header[28] = (byteRate and 0xff).toByte()
  header[29] = ((byteRate shr 8) and 0xff).toByte()
  header[30] = ((byteRate shr 16) and 0xff).toByte()
  header[31] = ((byteRate shr 24) and 0xff).toByte()
  header[32] = blockAlign.toByte()
  header[33] = 0
  header[34] = bitsPerSample.toByte()
  header[35] = 0

  header[36] = 'd'.code.toByte()
  header[37] = 'a'.code.toByte()
  header[38] = 't'.code.toByte()
  header[39] = 'a'.code.toByte()
  header[40] = (dataSize and 0xff).toByte()
  header[41] = ((dataSize shr 8) and 0xff).toByte()
  header[42] = ((dataSize shr 16) and 0xff).toByte()
  header[43] = ((dataSize shr 24) and 0xff).toByte()

  return header
}
