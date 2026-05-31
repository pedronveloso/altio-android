/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.download

import java.io.File
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class Sha256VerifierTest {

  @TempDir lateinit var tempDir: File

  @Test
  fun `verify returns true for matching hash`() {
    val content = "Hello, Altio AI!".toByteArray()
    val file = File(tempDir, "test.bin").also { it.writeBytes(content) }
    val expected = sha256Hex(content)
    assertTrue(Sha256Verifier.verify(file, expected))
  }

  @Test
  fun `verify returns false for mismatched hash`() {
    val file = File(tempDir, "test.bin").also { it.writeText("correct content") }
    assertFalse(Sha256Verifier.verify(file, "wronghash"))
  }

  @Test
  fun `verify returns false when file does not exist`() {
    assertFalse(Sha256Verifier.verify(File(tempDir, "missing.bin"), "any"))
  }

  @Test
  fun `verify is case insensitive for hex strings`() {
    val content = "test".toByteArray()
    val file = File(tempDir, "test.bin").also { it.writeBytes(content) }
    val expected = sha256Hex(content)
    assertTrue(Sha256Verifier.verify(file, expected.uppercase()))
    assertTrue(Sha256Verifier.verify(file, expected.lowercase()))
  }

  @Test
  fun `verify detects corruption in large file`() {
    val original = ByteArray(32_768) { it.toByte() }
    val file = File(tempDir, "large.bin").also { it.writeBytes(original) }
    val expected = sha256Hex(original)

    // Corrupt one byte
    val corrupted = original.clone().also { it[1000] = (it[1000] + 1).toByte() }
    file.writeBytes(corrupted)

    assertFalse(Sha256Verifier.verify(file, expected))
  }

  private fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
  }
}
