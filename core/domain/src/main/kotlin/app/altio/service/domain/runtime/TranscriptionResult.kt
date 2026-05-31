/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.runtime

data class TranscriptionResult(
    val transcript: String,
    val language: String,
    val durationMs: Long,
)
