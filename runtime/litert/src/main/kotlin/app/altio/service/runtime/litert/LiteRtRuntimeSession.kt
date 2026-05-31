/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.runtime.litert

import app.altio.service.domain.runtime.FinishReason
import app.altio.service.domain.runtime.InferenceChunk
import app.altio.service.domain.runtime.InferenceRequest
import app.altio.service.domain.runtime.Part
import app.altio.service.domain.runtime.Role
import app.altio.service.domain.runtime.RuntimeSession
import app.altio.service.domain.runtime.SessionParams
import app.altio.service.domain.runtime.TokenUsage
import app.altio.service.domain.runtime.TranscriptionResult
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LiteRtRuntimeSession(
    override val sessionId: String,
    private var conversation: com.google.ai.edge.litertlm.Conversation,
    private val params: SessionParams,
    private val engine: Engine,
) : RuntimeSession {

  private val mutex = Mutex()
  private var tokenIndex = 0
  private var completionTokenCount = 0

  override fun generateStream(request: InferenceRequest): Flow<InferenceChunk> {
    val lastUserMessage =
        request.messages.lastOrNull { it.role == Role.USER }
            ?: return kotlinx.coroutines.flow.flow {
              emit(InferenceChunk.Error(IllegalArgumentException("No user message in request")))
            }

    val contentItems = mutableListOf<Content>()
    for (part in lastUserMessage.parts) {
      when (part) {
        is Part.Audio -> contentItems.add(Content.AudioBytes(part.rawBytes))
        is Part.Image -> contentItems.add(Content.ImageBytes(part.pngBytes))
        is Part.Text -> if (part.text.isNotBlank()) contentItems.add(Content.Text(part.text))
      }
    }
    if (contentItems.isEmpty()) {
      return kotlinx.coroutines.flow.flow {
        emit(InferenceChunk.Error(IllegalArgumentException("Empty message content")))
      }
    }

    val sendContents = Contents.of(contentItems)
    var localCompletionTokens = 0

    return callbackFlow {
          conversation.sendMessageAsync(
              sendContents,
              object : MessageCallback {
                override fun onMessage(message: Message) {
                  localCompletionTokens++
                  tokenIndex++
                  trySend(InferenceChunk.Token(text = message.toString(), index = tokenIndex - 1))
                }

                override fun onDone() {
                  completionTokenCount += localCompletionTokens
                  trySend(
                      InferenceChunk.Done(
                          finishReason = FinishReason.STOP,
                          usage =
                              TokenUsage(
                                  promptTokens = 0,
                                  completionTokens = localCompletionTokens,
                              ),
                      )
                  )
                  close()
                }

                override fun onError(throwable: Throwable) {
                  if (throwable is CancellationException) {
                    runCatching { conversation.cancelProcess() }
                    close()
                  } else {
                    close(throwable)
                  }
                }
              },
              emptyMap(),
          )
          awaitClose { runCatching { conversation.cancelProcess() } }
        }
        .catch { cause -> emit(InferenceChunk.Error(cause)) }
  }

  override suspend fun transcribe(audioBytes: ByteArray, mimeType: String): TranscriptionResult {
    throw UnsupportedOperationException(
        "Standalone transcription is not supported by LiteRT-LM. Use generateStream with Part.Audio instead."
    )
  }

  override suspend fun reset() =
      mutex.withLock {
        conversation.close()
        val samplerConfig =
            SamplerConfig(
                topK = params.generationConfig.topK,
                topP = params.generationConfig.topP.toDouble(),
                temperature = params.generationConfig.temperature.toDouble(),
            )
        conversation =
            engine.createConversation(
                ConversationConfig(
                    systemInstruction = params.systemPrompt?.let { Contents.of(it) },
                    samplerConfig = samplerConfig,
                )
            )
        tokenIndex = 0
        completionTokenCount = 0
      }

  override fun close() {
    conversation.close()
  }
}
