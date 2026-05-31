/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.download

import java.io.File
import java.security.MessageDigest

object Sha256Verifier {
  private const val BUFFER_SIZE = 8_192

  /**
   * Reads [file] in chunks and verifies its SHA-256 hash matches [expected] (hex, lowercase).
   * Returns `true` when the hashes match, `false` otherwise or if the file does not exist.
   */
  fun verify(file: File, expected: String): Boolean {
    if (!file.exists()) return false
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered(BUFFER_SIZE).use { stream ->
      val buffer = ByteArray(BUFFER_SIZE)
      var bytesRead: Int
      while (stream.read(buffer).also { bytesRead = it } != -1) {
        digest.update(buffer, 0, bytesRead)
      }
    }
    return digest.digest().toHexString().equals(expected, ignoreCase = true)
  }

  private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
