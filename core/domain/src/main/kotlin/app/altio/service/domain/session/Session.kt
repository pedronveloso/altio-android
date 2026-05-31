/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.session

import app.altio.service.domain.runtime.GenerationConfig
import java.time.Instant

data class Session(
    val id: String,
    val clientId: String,
    val appName: String?,
    val modelId: String,
    val systemPrompt: String?,
    val generationConfig: GenerationConfig,
    val messageCount: Int,
    val createdAt: Instant,
    val lastActiveAt: Instant,
)
