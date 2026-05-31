/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.runtime

sealed class InferenceChunk {
  data class Token(val text: String, val index: Int) : InferenceChunk()

  data class Done(val finishReason: FinishReason, val usage: TokenUsage) : InferenceChunk()

  data class Error(val cause: Throwable) : InferenceChunk()
}

enum class FinishReason {
  STOP,
  MAX_TOKENS,
  CANCELLED,
  ERROR,
}

data class TokenUsage(val promptTokens: Int, val completionTokens: Int) {
  val totalTokens: Int
    get() = promptTokens + completionTokens
}
