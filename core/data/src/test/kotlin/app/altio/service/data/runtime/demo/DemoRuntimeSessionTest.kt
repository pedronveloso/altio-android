/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.runtime.demo

import app.altio.service.domain.runtime.InferenceChunk
import app.altio.service.domain.runtime.InferenceRequest
import app.altio.service.domain.runtime.Message
import app.altio.service.domain.runtime.Part
import app.altio.service.domain.runtime.Role
import app.altio.service.domain.runtime.SessionParams
import kotlin.random.Random
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DemoRuntimeSessionTest {

  @Test
  fun `generateStream emits tokens and done event`() = runTest {
    val session =
        DemoRuntimeSession(
            sessionId = "demo-session",
            params = SessionParams(systemPrompt = "Be concise."),
            random = Random(0),
        )

    val events =
        session
            .generateStream(
                InferenceRequest(listOf(Message(Role.USER, listOf(Part.Text("Test prompt")))))
            )
            .toList()

    assertTrue(events.any { it is InferenceChunk.Token })
    assertTrue(events.last() is InferenceChunk.Done)
  }

  @Test
  fun `transcribe returns canned transcript`() = runTest {
    val session =
        DemoRuntimeSession(
            sessionId = "demo-session",
            params = SessionParams(),
            random = Random(1),
        )

    val result = session.transcribe(ByteArray(128), "audio/mpeg")

    assertTrue(result.transcript.contains("Demo transcript"))
    assertTrue(result.language.isNotBlank())
  }
}
