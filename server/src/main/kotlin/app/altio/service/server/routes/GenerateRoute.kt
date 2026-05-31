/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server.routes

import app.altio.sdk.contract.error.ApiErrorCode
import app.altio.sdk.contract.generate.GenerateRequest
import app.altio.sdk.contract.generate.MessageRole
import app.altio.sdk.contract.job.SubmitJobResponse
import app.altio.service.data.runtime.InferenceScheduler
import app.altio.service.data.runtime.SchedulerWork
import app.altio.service.data.session.SessionCoordinator
import app.altio.service.domain.job.Job
import app.altio.service.domain.job.JobRepository
import app.altio.service.domain.job.JobStatus
import app.altio.service.domain.job.JobType
import app.altio.service.domain.runtime.InferenceRequest
import app.altio.service.domain.runtime.Message
import app.altio.service.domain.runtime.Part
import app.altio.service.domain.runtime.Role
import app.altio.service.domain.session.SessionRepository
import app.altio.service.server.ClientPrincipal
import app.altio.service.server.respondApiError
import app.altio.service.server.toSdkStatus
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first

private const val MAX_JOBS_PER_CLIENT = 5
private const val MAX_GENERATE_BODY_BYTES = 1 * 1024 * 1024 // 1 MB

fun Route.generateRoute(
    sessionRepository: SessionRepository,
    sessionCoordinator: SessionCoordinator,
    jobRepository: JobRepository,
    inferenceScheduler: InferenceScheduler,
) {
  post("/v1/sessions/{id}/generate") {
    val caller = call.principal<ClientPrincipal>()!!
    val sessionId = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)

    // 1 MB body limit for JSON generation requests.
    val bodyLength = call.request.contentLength() ?: 0L
    if (bodyLength > MAX_GENERATE_BODY_BYTES) {
      respondApiError(
          status = HttpStatusCode.PayloadTooLarge,
          code = ApiErrorCode.REQUEST_TOO_LARGE,
          message = "Request body exceeds 1 MB",
      )
      return@post
    }

    val session = sessionRepository.getSession(sessionId).first()
    if (session == null || session.clientId != caller.clientId) {
      respondApiError(
          status = HttpStatusCode.NotFound,
          code = ApiErrorCode.SESSION_NOT_FOUND,
          message = "Session $sessionId does not exist",
      )
      return@post
    }

    // Per-client active job cap.
    val activeJobs = jobRepository.countActiveByClient(caller.clientId)
    if (activeJobs >= MAX_JOBS_PER_CLIENT) {
      respondApiError(
          status = HttpStatusCode.TooManyRequests,
          code = ApiErrorCode.RATE_LIMITED,
          message = "Max $MAX_JOBS_PER_CLIENT concurrent jobs per client",
      )
      return@post
    }

    val req = call.receive<GenerateRequest>()
    val messages =
        req.messages.map { dto ->
          val role =
              when (dto.role) {
                MessageRole.USER -> Role.USER
                MessageRole.MODEL,
                MessageRole.ASSISTANT -> Role.MODEL
                MessageRole.SYSTEM -> Role.SYSTEM
              }
          Message(role = role, parts = listOf(Part.Text(dto.content)))
        }

    if (messages.none { it.role == Role.USER }) {
      respondApiError(
          status = HttpStatusCode.BadRequest,
          code = ApiErrorCode.INVALID_INPUT,
          message = "At least one user message is required",
      )
      return@post
    }

    val jobId = UUID.randomUUID().toString()
    val job =
        Job(
            id = jobId,
            sessionId = sessionId,
            clientId = caller.clientId,
            appName = session.appName,
            type = JobType.GENERATE,
            status = JobStatus.QUEUED,
            createdAt = Instant.now(),
        )
    jobRepository.createJob(job)
    if (
        !inferenceScheduler.enqueue(
            SchedulerWork.Generate(jobId, sessionId, InferenceRequest(messages))
        )
    ) {
      jobRepository.fail(jobId, "JOB_QUEUE_FULL", Instant.now())
      respondApiError(
          status = HttpStatusCode.ServiceUnavailable,
          code = ApiErrorCode.INTERNAL_ERROR,
          message = "The inference queue is full. Retry shortly.",
      )
      return@post
    }
    sessionCoordinator.incrementMessageCount(sessionId)

    call.respond(
        HttpStatusCode.Accepted,
        SubmitJobResponse(jobId = jobId, status = JobStatus.QUEUED.toSdkStatus()),
    )
  }
}
