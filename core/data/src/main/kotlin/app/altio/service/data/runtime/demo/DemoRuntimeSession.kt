/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.runtime.demo

import app.altio.service.domain.runtime.FinishReason
import app.altio.service.domain.runtime.InferenceChunk
import app.altio.service.domain.runtime.InferenceRequest
import app.altio.service.domain.runtime.Part
import app.altio.service.domain.runtime.Role
import app.altio.service.domain.runtime.RuntimeSession
import app.altio.service.domain.runtime.SessionParams
import app.altio.service.domain.runtime.TokenUsage
import app.altio.service.domain.runtime.TranscriptionResult
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DemoRuntimeSession(
    override val sessionId: String,
    private val params: SessionParams,
    private val random: Random,
) : RuntimeSession {

  private val conversation = mutableListOf<String>()

  override fun generateStream(request: InferenceRequest): Flow<InferenceChunk> = flow {
    val prompt =
        request.messages
            .lastOrNull { it.role == Role.USER }
            ?.parts
            ?.filterIsInstance<Part.Text>()
            ?.joinToString(" ") { it.text }
            ?: throw IllegalArgumentException("No user message in request")
    conversation += prompt

    val response = cannedTextResponses(prompt).random(random)
    val chunks = response.split(" ").filter { it.isNotBlank() }
    chunks.forEachIndexed { index, chunk ->
      delay(35)
      emit(
          InferenceChunk.Token(
              text = if (index == chunks.lastIndex) chunk else "$chunk ",
              index = index,
          )
      )
    }
    emit(
        InferenceChunk.Done(
            finishReason = FinishReason.STOP,
            usage = TokenUsage(promptTokens = prompt.wordCount(), completionTokens = chunks.size),
        )
    )
  }

  override suspend fun transcribe(audioBytes: ByteArray, mimeType: String): TranscriptionResult {
    delay(150)
    return cannedTranscripts(audioBytes.size).random(random)
  }

  override suspend fun reset() {
    conversation.clear()
  }

  override fun close() = Unit

  private fun cannedTextResponses(prompt: String): List<String> =
      listOf(
          "Demo model reply: I received \"$prompt\" and returned a mocked response from the service app.",
          "This is a built-in response for local integration testing. No on-device LLM was loaded for this run.",
          "Manual testing path confirmed. The service accepted the request, opened a session, and streamed a randomized demo answer.",
      ) +
          params.systemPrompt
              ?.let { systemPrompt ->
                listOf(
                    "System prompt acknowledged for demo mode: $systemPrompt. Prompt received: $prompt"
                )
              }
              .orEmpty()

  private fun cannedTranscripts(byteCount: Int): List<TranscriptionResult> =
      listOf(
          TranscriptionResult(
              transcript = "Demo transcript: checking the audio path through the local service.",
              language = "en",
              durationMs = 2_400,
          ),
          TranscriptionResult(
              transcript =
                  "Demo transcript: the client uploaded audio successfully and received a mocked result.",
              language = "en",
              durationMs = 3_100,
          ),
          TranscriptionResult(
              transcript =
                  "Demo transcript: received approximately $byteCount bytes of MP3 audio for testing.",
              language = "en",
              durationMs = 1_800,
          ),
      )

  private fun String.wordCount(): Int = trim().split(Regex("\\s+")).count { it.isNotEmpty() }
}
