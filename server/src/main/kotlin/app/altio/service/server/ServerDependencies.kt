/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server

import app.altio.service.data.runtime.InferenceScheduler
import app.altio.service.data.runtime.RuntimeEngineHolder
import app.altio.service.data.runtime.RuntimeSessionManager
import app.altio.service.domain.job.JobRepository
import app.altio.service.domain.model.ModelRepository
import app.altio.service.domain.session.SessionRepository
import app.altio.service.domain.settings.SettingsRepository
import app.altio.service.domain.token.TokenRepository

/**
 * Aggregates all service-layer dependencies needed by Ktor routes. Passed as a single object to
 * [AiHttpServer] to avoid long constructor parameter lists.
 */
data class ServerDependencies(
    val modelRepository: ModelRepository,
    val sessionRepository: SessionRepository,
    val jobRepository: JobRepository,
    val sessionManager: RuntimeSessionManager,
    val inferenceScheduler: InferenceScheduler,
    val settingsRepository: SettingsRepository,
    val tokenRepository: TokenRepository,
    val engineHolder: RuntimeEngineHolder,
)
