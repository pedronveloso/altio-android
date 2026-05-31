/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server.routes

import app.altio.sdk.contract.error.ApiErrorCode
import app.altio.sdk.contract.stream.DoneEvent
import app.altio.sdk.contract.stream.SseErrorEvent
import app.altio.sdk.contract.stream.TokenEvent
import app.altio.service.data.runtime.InferenceScheduler
import app.altio.service.domain.job.JobRepository
import app.altio.service.domain.runtime.InferenceChunk
import app.altio.service.server.ClientPrincipal
import app.altio.service.server.respondApiError
import app.altio.service.server.toJobStatusResponse
import app.altio.service.server.toSdkFinishReason
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val sseJson = Json {
  encodeDefaults = true
  explicitNulls = false
}

fun Route.jobsRoute(jobRepository: JobRepository, inferenceScheduler: InferenceScheduler) {
  post("/v1/jobs/{id}/cancel") {
    val caller = call.principal<ClientPrincipal>()!!
    val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
    val job = jobRepository.getJob(id).first()
    if (job == null || job.clientId != caller.clientId) {
      respondApiError(
          status = HttpStatusCode.NotFound,
          code = ApiErrorCode.JOB_NOT_FOUND,
          message = "Job $id does not exist",
      )
      return@post
    }
    if (!job.status.isCancellable()) {
      respondApiError(
          status = HttpStatusCode.Conflict,
          code = ApiErrorCode.CANCELLED,
          message = "Job is already in terminal state: ${job.status.name.lowercase()}",
      )
      return@post
    }
    inferenceScheduler.cancelJob(id)
    call.respond(HttpStatusCode.NoContent)
  }

  get("/v1/jobs/{id}") {
    val caller = call.principal<ClientPrincipal>()!!
    val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
    val job = jobRepository.getJob(id).first()
    if (job == null || job.clientId != caller.clientId) {
      respondApiError(
          status = HttpStatusCode.NotFound,
          code = ApiErrorCode.JOB_NOT_FOUND,
          message = "Job $id does not exist",
      )
      return@get
    }
    call.respond(job.toJobStatusResponse())
  }

  sse("/v1/jobs/{id}/stream") {
    val caller = call.principal<ClientPrincipal>()!!
    val id =
        call.parameters["id"]
            ?: run {
              send(
                  ServerSentEvent(
                      data = sseJson.encodeToString(SseErrorEvent("missing job id")),
                      event = "error",
                  )
              )
              return@sse
            }

    // Verify ownership before streaming — return error event rather than 401 since we're
    // already in an SSE context and the client may have established the connection already.
    val job = jobRepository.getJob(id).first()
    if (job == null || job.clientId != caller.clientId) {
      send(
          ServerSentEvent(
              data = sseJson.encodeToString(SseErrorEvent("JOB_NOT_FOUND")),
              event = "error",
          )
      )
      return@sse
    }

    val stream =
        inferenceScheduler.streamJob(id)
            ?: run {
              send(
                  ServerSentEvent(
                      data = sseJson.encodeToString(SseErrorEvent("job not found")),
                      event = "error",
                  )
              )
              return@sse
            }

    val pingJob = launch {
      while (true) {
        delay(KEEP_ALIVE_INTERVAL_MS)
        send(ServerSentEvent(comments = "ping"))
      }
    }

    try {
      stream
          .transformWhile { chunk ->
            emit(chunk)
            chunk is InferenceChunk.Token
          }
          .collect { chunk ->
            val event =
                when (chunk) {
                  is InferenceChunk.Token ->
                      ServerSentEvent(
                          data = sseJson.encodeToString(TokenEvent(chunk.text, chunk.index)),
                          event = "token",
                      )
                  is InferenceChunk.Done ->
                      ServerSentEvent(
                          data =
                              sseJson.encodeToString(
                                  DoneEvent(
                                      finishReason = chunk.finishReason.toSdkFinishReason(),
                                      promptTokens = chunk.usage.promptTokens,
                                      completionTokens = chunk.usage.completionTokens,
                                  )
                              ),
                          event = "done",
                      )
                  is InferenceChunk.Error ->
                      ServerSentEvent(
                          data = sseJson.encodeToString(SseErrorEvent(chunk.cause.message)),
                          event = "error",
                      )
                }
            send(event)
          }
    } finally {
      pingJob.cancel()
    }
  }
}

private const val KEEP_ALIVE_INTERVAL_MS = 15_000L
