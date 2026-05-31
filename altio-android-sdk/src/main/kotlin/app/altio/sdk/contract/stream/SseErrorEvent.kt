/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.contract.stream

import kotlinx.serialization.Serializable

@Serializable data class SseErrorEvent(val message: String?)
