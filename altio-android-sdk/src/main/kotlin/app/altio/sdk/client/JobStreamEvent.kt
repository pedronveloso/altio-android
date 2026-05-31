/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.client

import app.altio.sdk.contract.stream.FinishReason

sealed interface JobStreamEvent {
  data class Token(val text: String, val index: Int) : JobStreamEvent

  data class Done(
      val finishReason: FinishReason,
      val promptTokens: Int,
      val completionTokens: Int,
  ) : JobStreamEvent

  data class Error(val message: String?) : JobStreamEvent
}
