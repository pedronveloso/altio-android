/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server.routes

import app.altio.sdk.contract.error.ApiErrorCode
import app.altio.sdk.contract.session.CreateSessionRequest
import app.altio.service.data.session.ActiveRuntimeSessionException
import app.altio.service.data.session.SessionCoordinator
import app.altio.service.domain.model.ModelRepository
import app.altio.service.domain.model.ModelStatus
import app.altio.service.domain.runtime.GenerationConfig
import app.altio.service.domain.runtime.RuntimeConfig
import app.altio.service.domain.runtime.SessionParams
import app.altio.service.domain.session.Session
import app.altio.service.domain.session.SessionRepository
import app.altio.service.domain.settings.SettingsRepository
import app.altio.service.domain.settings.resolveAccelerator
import app.altio.service.server.ClientPrincipal
import app.altio.service.server.respondApiError
import app.altio.service.server.toSessionDetailResponse
import app.altio.service.server.toSessionResponse
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import timber.log.Timber

private const val MAX_SESSIONS_PER_CLIENT = 20

fun Route.sessionsRoute(
    modelRepository: ModelRepository,
    settingsRepository: SettingsRepository,
    sessionRepository: SessionRepository,
    sessionCoordinator: SessionCoordinator,
) {
  post("/v1/sessions") {
    val caller = call.principal<ClientPrincipal>()!!

    // Per-client session cap.
    val sessionCount = sessionRepository.countByClient(caller.clientId)
    if (sessionCount >= MAX_SESSIONS_PER_CLIENT) {
      respondApiError(
          status = HttpStatusCode.TooManyRequests,
          code = ApiErrorCode.RATE_LIMITED,
          message = "Max $MAX_SESSIONS_PER_CLIENT sessions per client",
      )
      return@post
    }

    val req = call.receive<CreateSessionRequest>()
    val requestedModelId = req.modelId
    val resolvedModelId = requestedModelId ?: settingsRepository.settings.first().activeModelId
    Timber.i(
        "Creating session for client %s with requestedModelId=%s resolvedModelId=%s",
        caller.clientId,
        requestedModelId,
        resolvedModelId,
    )

    val model = modelRepository.getModel(resolvedModelId).first()
    if (model == null) {
      respondApiError(
          status = HttpStatusCode.NotFound,
          code = ApiErrorCode.MODEL_NOT_FOUND,
          message = "Model $resolvedModelId does not exist",
      )
      return@post
    }
    if (model.status != ModelStatus.READY && model.status != ModelStatus.LOADED) {
      respondApiError(
          status = HttpStatusCode.Conflict,
          code = ApiErrorCode.MODEL_NOT_READY,
          message = "Model is not ready (status: ${model.status.name.lowercase()})",
      )
      return@post
    }
    val settings = settingsRepository.settings.first()
    val runtimeConfig =
        RuntimeConfig(
            accelerator = settings.resolveAccelerator(model),
            maxTokens = settings.maxTokens,
        )
    val now = Instant.now()
    val sessionId = UUID.randomUUID().toString()
    val generationConfig =
        GenerationConfig(
            temperature = req.temperature,
            topK = req.topK,
            topP = req.topP,
            maxTokens = req.maxTokens,
        )
    val params = SessionParams(systemPrompt = req.systemPrompt, generationConfig = generationConfig)
    val session =
        Session(
            id = sessionId,
            clientId = caller.clientId,
            appName = req.appName?.trim()?.takeIf { it.isNotEmpty() },
            modelId = resolvedModelId,
            systemPrompt = req.systemPrompt,
            generationConfig = generationConfig,
            messageCount = 0,
            createdAt = now,
            lastActiveAt = now,
        )

    runCatching { sessionCoordinator.createSession(session, model, runtimeConfig, params) }
        .onFailure { e ->
          if (e is ActiveRuntimeSessionException) {
            respondApiError(
                status = HttpStatusCode.Conflict,
                code = ApiErrorCode.MODEL_IN_USE,
                message =
                    e.message ?: "LiteRT currently supports one active runtime session at a time.",
            )
            return@post
          }
          Timber.e(e, "Failed to open runtime session for model %s", resolvedModelId)
          respondApiError(
              status = HttpStatusCode.ServiceUnavailable,
              code = ApiErrorCode.MODEL_NOT_LOADED,
              message = e.message ?: "Failed to open runtime session",
          )
          return@post
        }

    call.respond(HttpStatusCode.Created, session.toSessionResponse())
  }

  get("/v1/sessions/{id}") {
    val caller = call.principal<ClientPrincipal>()!!
    val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
    val session = sessionRepository.getSession(id).first()
    if (session == null || session.clientId != caller.clientId) {
      respondApiError(
          status = HttpStatusCode.NotFound,
          code = ApiErrorCode.SESSION_NOT_FOUND,
          message = "Session $id does not exist",
      )
      return@get
    }
    call.respond(session.toSessionDetailResponse())
  }

  post("/v1/sessions/{id}/reset") {
    val caller = call.principal<ClientPrincipal>()!!
    val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
    val session = sessionRepository.getSession(id).first()
    if (session == null || session.clientId != caller.clientId) {
      respondApiError(
          status = HttpStatusCode.NotFound,
          code = ApiErrorCode.SESSION_NOT_FOUND,
          message = "Session $id does not exist",
      )
      return@post
    }
    sessionCoordinator.resetSession(id)
    call.respond(HttpStatusCode.NoContent)
  }

  delete("/v1/sessions/{id}") {
    val caller = call.principal<ClientPrincipal>()!!
    val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
    val session = sessionRepository.getSession(id).first()
    if (session == null || session.clientId != caller.clientId) {
      respondApiError(
          status = HttpStatusCode.NotFound,
          code = ApiErrorCode.SESSION_NOT_FOUND,
          message = "Session $id does not exist",
      )
      return@delete
    }
    sessionCoordinator.deleteSession(id)
    call.respond(HttpStatusCode.NoContent)
  }
}
