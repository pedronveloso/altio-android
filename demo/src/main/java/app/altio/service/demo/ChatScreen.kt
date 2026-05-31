/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.altio.sdk.client.AltioAiServiceClient
import app.altio.sdk.client.JobStreamEvent
import app.altio.sdk.client.NotFoundException
import app.altio.sdk.contract.error.ApiErrorCode
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(
    val role: Role,
    val text: String,
    val id: Long = nextId(),
) {
  enum class Role {
    USER,
    MODEL,
  }

  private companion object {
    val idCounter = AtomicLong(0L)

    fun nextId(): Long = idCounter.incrementAndGet()
  }
}

@Stable
class ChatConversationState
private constructor(
    private val client: AltioAiServiceClient,
    private val sessionId: String,
    private val trackedJobIds: SnapshotStateList<String>,
    private val onSessionInvalid: suspend () -> String,
    private val scope: CoroutineScope,
) {
  val messages = mutableStateListOf<ChatMessage>()
  var inputText by mutableStateOf("")
    private set

  var isStreaming by mutableStateOf(false)
    private set

  var streamingText by mutableStateOf("")
    private set

  fun updateInput(text: String) {
    inputText = text
  }

  fun sendMessage() {
    val text = inputText.trim()
    if (text.isBlank() || isStreaming) return
    inputText = ""
    messages += ChatMessage(ChatMessage.Role.USER, text)
    isStreaming = true
    streamingText = ""

    scope.launch {
      try {
        val job =
            withContext(Dispatchers.IO) {
              runCatching { client.generate(sessionId, text) }
                  .recoverCatching { error ->
                    if (!error.isInvalidSession()) throw error
                    client.generate(onSessionInvalid(), text)
                  }
                  .getOrThrow()
            }
        trackedJobIds += job.jobId
        withContext(Dispatchers.IO) {
          client.streamJob(job.jobId).collect { event ->
            withContext(Dispatchers.Main.immediate) {
              when (event) {
                is JobStreamEvent.Token -> streamingText += event.text
                is JobStreamEvent.Done -> finishStreamingMessage(streamingText)
                is JobStreamEvent.Error -> finishStreamingMessage("Error: ${event.message}")
              }
            }
          }
        }
      } catch (e: Exception) {
        finishStreamingMessage("Error: ${e.message}")
      }
    }
  }

  private fun finishStreamingMessage(text: String) {
    messages += ChatMessage(ChatMessage.Role.MODEL, text)
    streamingText = ""
    isStreaming = false
  }

  companion object {
    fun create(
        client: AltioAiServiceClient,
        sessionId: String,
        trackedJobIds: SnapshotStateList<String>,
        onSessionInvalid: suspend () -> String,
        scope: CoroutineScope,
    ): ChatConversationState =
        ChatConversationState(
            client = client,
            sessionId = sessionId,
            trackedJobIds = trackedJobIds,
            onSessionInvalid = onSessionInvalid,
            scope = scope,
        )
  }
}

@Composable
fun rememberChatConversationState(
    client: AltioAiServiceClient,
    sessionId: String,
    trackedJobIds: SnapshotStateList<String>,
    onSessionInvalid: suspend () -> String,
): ChatConversationState {
  val scope = rememberCoroutineScope()
  return remember(client, sessionId, trackedJobIds, onSessionInvalid, scope) {
    ChatConversationState.create(
        client = client,
        sessionId = sessionId,
        trackedJobIds = trackedJobIds,
        onSessionInvalid = onSessionInvalid,
        scope = scope,
    )
  }
}

private fun Throwable.isInvalidSession(): Boolean =
    this is NotFoundException && apiError?.code == ApiErrorCode.SESSION_NOT_FOUND

/**
 * Minimal chat screen that demonstrates real-time token streaming from the Altio service.
 *
 * @param chatState Shared conversation state that survives tab switches.
 */
@Composable
fun ChatScreen(
    chatState: ChatConversationState,
    modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()

  LaunchedEffect(chatState.messages.size, chatState.streamingText) {
    if (chatState.messages.isNotEmpty() || chatState.streamingText.isNotEmpty()) {
      listState.animateScrollToItem(chatState.messages.size)
    }
  }

  Column(modifier = modifier.fillMaxSize().imePadding()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      itemsIndexed(
          items = chatState.messages,
          key = { _, message -> message.id },
      ) { _, message ->
        MessageBubble(message = message)
      }
      if (chatState.isStreaming) {
        item { StreamingBubble(text = chatState.streamingText) }
      }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
          value = chatState.inputText,
          onValueChange = chatState::updateInput,
          modifier = Modifier.weight(1f).testTag("chat-input"),
          placeholder = { Text("Message…") },
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(onSend = { chatState.sendMessage() }),
          singleLine = true,
          enabled = !chatState.isStreaming,
      )
      Spacer(modifier = Modifier.width(8.dp))
      Button(
          onClick = chatState::sendMessage,
          enabled = chatState.inputText.isNotBlank() && !chatState.isStreaming,
          modifier = Modifier.testTag("chat-send"),
      ) {
        Text("Send")
      }
    }
  }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
  val isUser = message.role == ChatMessage.Role.USER
  val contentColor =
      if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
      else MaterialTheme.colorScheme.onSurfaceVariant

  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
  ) {
    Box(
        modifier =
            Modifier.widthIn(max = 280.dp)
                .background(
                    color =
                        if (isUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
      if (isUser) {
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
        )
      } else {
        MarkdownText(markdown = message.text, color = contentColor)
      }
    }
  }
}

@Composable
private fun StreamingBubble(text: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
    Box(
        modifier =
            Modifier.widthIn(max = 280.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
      if (text.isEmpty()) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
      } else {
        MarkdownText(
            markdown = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun UserMessageBubblePreview() {
  MaterialTheme {
    Surface { MessageBubble(message = ChatMessage(ChatMessage.Role.USER, "What can you do?")) }
  }
}

@Preview(showBackground = true)
@Composable
private fun ModelMarkdownBubblePreview() {
  MaterialTheme {
    Surface {
      MessageBubble(
          message =
              ChatMessage(
                  ChatMessage.Role.MODEL,
                  """
                  # Summary

                  **Altio** supports *native* markdown in the chat demo.

                  - Headings
                  - Inline emphasis
                  - Bullet points
                  """
                      .trimIndent(),
              )
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun StreamingBubblePreview() {
  MaterialTheme {
    Surface {
      StreamingBubble(
          text =
              """
              ## Streaming
              - Token one
              - **Token two**
              """
                  .trimIndent()
      )
    }
  }
}
