/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server.audio

import android.media.MediaDataSource

/**
 * [MediaDataSource] backed by a [ByteArray], so [android.media.MediaExtractor] can read in-memory
 * audio bytes without writing a temporary file to disk.
 */
internal class ByteArrayMediaDataSource(private val data: ByteArray) : MediaDataSource() {

  override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
    if (position >= data.size) return -1
    val toRead = minOf(size, (data.size - position).toInt())
    System.arraycopy(data, position.toInt(), buffer, offset, toRead)
    return toRead
  }

  override fun getSize(): Long = data.size.toLong()

  override fun close() {}
}
