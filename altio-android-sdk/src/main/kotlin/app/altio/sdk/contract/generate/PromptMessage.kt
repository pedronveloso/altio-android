/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.contract.generate

import kotlinx.serialization.Serializable

@Serializable data class PromptMessage(val role: MessageRole, val content: String)
