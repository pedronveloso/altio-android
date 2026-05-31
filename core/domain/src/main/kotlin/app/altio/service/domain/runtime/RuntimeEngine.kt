/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.runtime

/** A loaded model engine. Creates isolated [RuntimeSession]s. Thread-safe. */
interface RuntimeEngine : AutoCloseable {
  /** Creates a new session with its own conversation history. */
  suspend fun createSession(sessionId: String, params: SessionParams): RuntimeSession

  /** Releases all native resources held by this engine. */
  override fun close()
}

data class SessionParams(
    val systemPrompt: String? = null,
    val generationConfig: GenerationConfig = GenerationConfig(),
)
