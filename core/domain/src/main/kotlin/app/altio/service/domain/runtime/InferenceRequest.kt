/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.runtime

data class InferenceRequest(
    val messages: List<Message>,
    val config: GenerationConfig = GenerationConfig(),
) {
  init {
    require(messages.isNotEmpty()) { "messages must not be empty" }
  }
}

data class Message(val role: Role, val parts: List<Part>)

enum class Role {
  SYSTEM,
  USER,
  MODEL,
}

sealed class Part {
  data class Text(val text: String) : Part()

  data class Image(val pngBytes: ByteArray) : Part() {
    override fun equals(other: Any?) = other is Image && pngBytes.contentEquals(other.pngBytes)

    override fun hashCode() = pngBytes.contentHashCode()
  }

  data class Audio(val rawBytes: ByteArray) : Part() {
    override fun equals(other: Any?) = other is Audio && rawBytes.contentEquals(other.rawBytes)

    override fun hashCode() = rawBytes.contentHashCode()
  }
}
