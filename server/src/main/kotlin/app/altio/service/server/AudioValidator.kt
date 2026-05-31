/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server

/**
 * Validates audio uploads before they are submitted for transcription.
 *
 * v1 constraints:
 * - Format: common audio MIME types accepted by the SDK decode pipeline
 * - Max size: 25 MB
 */
object AudioValidator {

  const val MAX_SIZE_BYTES = 25L * 1024 * 1024 // 25 MB

  private val ALLOWED_MIME_TYPES =
      setOf(
          "audio/mpeg",
          "audio/mp3",
          "audio/mp4",
          "audio/m4a",
          "audio/x-m4a",
          "audio/aac",
          "audio/wav",
          "audio/x-wav",
          "audio/flac",
          "audio/ogg",
      )

  sealed class Result {
    object Valid : Result()

    data class Invalid(val code: String, val message: String) : Result()
  }

  fun validate(mimeType: String, sizeBytes: Long): Result {
    val normalised = mimeType.lowercase().substringBefore(";").trim()
    if (normalised !in ALLOWED_MIME_TYPES) {
      return Result.Invalid(
          code = "UNSUPPORTED_FORMAT",
          message = "Unsupported audio format. Got: $mimeType",
      )
    }
    if (sizeBytes > MAX_SIZE_BYTES) {
      return Result.Invalid(
          code = "REQUEST_TOO_LARGE",
          message = "Audio file exceeds 25 MB limit (received $sizeBytes bytes)",
      )
    }
    return Result.Valid
  }
}
