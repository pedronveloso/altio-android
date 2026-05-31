/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudioValidatorTest {

  @Test
  fun `valid MP3 by audio-mpeg MIME type passes`() {
    val result = AudioValidator.validate("audio/mpeg", 1024)
    assertEquals(AudioValidator.Result.Valid, result)
  }

  @Test
  fun `valid MP3 by audio-mp3 alias passes`() {
    val result = AudioValidator.validate("audio/mp3", 1024)
    assertEquals(AudioValidator.Result.Valid, result)
  }

  @Test
  fun `MIME type with charset parameter is normalised and accepted`() {
    val result = AudioValidator.validate("audio/mpeg; charset=binary", 1024)
    assertEquals(AudioValidator.Result.Valid, result)
  }

  @Test
  fun `AAC in MPEG-4 container is accepted`() {
    val result = AudioValidator.validate("audio/mp4", 1024)
    assertEquals(AudioValidator.Result.Valid, result)
  }

  @Test
  fun `OGG is accepted`() {
    val result = AudioValidator.validate("audio/ogg", 1024)
    assertEquals(AudioValidator.Result.Valid, result)
  }

  @Test
  fun `unsupported MIME type is rejected with UNSUPPORTED_FORMAT`() {
    val result = AudioValidator.validate("audio/webm", 1024)
    assertTrue(result is AudioValidator.Result.Invalid)
    assertEquals("UNSUPPORTED_FORMAT", (result as AudioValidator.Result.Invalid).code)
  }

  @Test
  fun `video MIME type is rejected`() {
    val result = AudioValidator.validate("video/mp4", 1024)
    assertTrue(result is AudioValidator.Result.Invalid)
    assertEquals("UNSUPPORTED_FORMAT", (result as AudioValidator.Result.Invalid).code)
  }

  @Test
  fun `application-octet-stream is rejected`() {
    val result = AudioValidator.validate("application/octet-stream", 1024)
    assertTrue(result is AudioValidator.Result.Invalid)
    assertEquals("UNSUPPORTED_FORMAT", (result as AudioValidator.Result.Invalid).code)
  }

  @Test
  fun `file exactly at 25 MB limit passes`() {
    val result = AudioValidator.validate("audio/mpeg", AudioValidator.MAX_SIZE_BYTES)
    assertEquals(AudioValidator.Result.Valid, result)
  }

  @Test
  fun `file one byte over 25 MB limit is rejected with REQUEST_TOO_LARGE`() {
    val result = AudioValidator.validate("audio/mpeg", AudioValidator.MAX_SIZE_BYTES + 1)
    assertTrue(result is AudioValidator.Result.Invalid)
    assertEquals("REQUEST_TOO_LARGE", (result as AudioValidator.Result.Invalid).code)
  }

  @Test
  fun `very large file is rejected`() {
    val result = AudioValidator.validate("audio/mpeg", 100L * 1024 * 1024)
    assertTrue(result is AudioValidator.Result.Invalid)
    assertEquals("REQUEST_TOO_LARGE", (result as AudioValidator.Result.Invalid).code)
  }

  @Test
  fun `wrong MIME type and oversized file - MIME check takes precedence`() {
    val result = AudioValidator.validate("video/mp4", AudioValidator.MAX_SIZE_BYTES + 1)
    assertTrue(result is AudioValidator.Result.Invalid)
    assertEquals("UNSUPPORTED_FORMAT", (result as AudioValidator.Result.Invalid).code)
  }
}
