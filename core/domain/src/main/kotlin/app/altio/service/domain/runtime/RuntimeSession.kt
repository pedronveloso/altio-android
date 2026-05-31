/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.runtime

import kotlinx.coroutines.flow.Flow

/** An isolated inference session. Holds per-conversation state. */
interface RuntimeSession : AutoCloseable {
  val sessionId: String

  /**
   * Streams token-by-token inference output. The returned [Flow] completes with
   * [InferenceChunk.Done] or terminates with [InferenceChunk.Error].
   */
  fun generateStream(request: InferenceRequest): Flow<InferenceChunk>

  /** Runs audio transcription. Suspends until the full transcript is ready. */
  suspend fun transcribe(audioBytes: ByteArray, mimeType: String): TranscriptionResult

  /** Clears conversation history while keeping the session open. */
  suspend fun reset()

  override fun close()
}
