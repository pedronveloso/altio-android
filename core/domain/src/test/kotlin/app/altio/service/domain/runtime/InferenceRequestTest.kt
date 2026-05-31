/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class InferenceRequestTest {

  @Test
  fun `constructor accepts non-empty message list`() {
    val request = InferenceRequest(messages = listOf(userTextMessage("hello")))
    assertEquals(1, request.messages.size)
  }

  @Test
  fun `constructor with empty messages throws IllegalArgumentException`() {
    assertThrows(IllegalArgumentException::class.java) { InferenceRequest(messages = emptyList()) }
  }

  @Test
  fun `exception message mentions messages`() {
    val ex =
        assertThrows(IllegalArgumentException::class.java) {
          InferenceRequest(messages = emptyList())
        }
    assertEquals("messages must not be empty", ex.message)
  }

  @Test
  fun `default generation config is applied when not specified`() {
    val request = InferenceRequest(messages = listOf(userTextMessage("hi")))
    assertEquals(GenerationConfig(), request.config)
  }

  @Test
  fun `custom generation config is preserved`() {
    val config = GenerationConfig(temperature = 0.5f, topK = 10)
    val request = InferenceRequest(messages = listOf(userTextMessage("hi")), config = config)
    assertEquals(config, request.config)
  }

  @Test
  fun `multiple messages are accepted`() {
    val messages =
        listOf(
            userTextMessage("first"),
            Message(role = Role.MODEL, parts = listOf(Part.Text("response"))),
            userTextMessage("follow-up"),
        )
    val request = InferenceRequest(messages = messages)
    assertEquals(3, request.messages.size)
  }

  private fun userTextMessage(text: String) =
      Message(role = Role.USER, parts = listOf(Part.Text(text)))
}
