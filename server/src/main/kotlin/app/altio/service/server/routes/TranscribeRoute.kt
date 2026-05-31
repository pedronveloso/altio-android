/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server.routes

import app.altio.sdk.contract.error.ApiErrorCode
import app.altio.sdk.contract.job.SubmitJobResponse
import app.altio.service.data.runtime.InferenceScheduler
import app.altio.service.data.session.SessionCoordinator
import app.altio.service.domain.job.JobRepository
import app.altio.service.domain.job.JobStatus
import app.altio.service.domain.job.JobType
import app.altio.service.domain.model.ModelCapability
import app.altio.service.domain.model.ModelRepository
import app.altio.service.domain.session.SessionRepository
import app.altio.service.server.AudioValidator
import app.altio.service.server.ClientPrincipal
import app.altio.service.server.audio.decodeToWav
import app.altio.service.server.respondApiError
import app.altio.service.server.toSdkStatus
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val MAX_TRANSCRIBE_JOBS_PER_CLIENT = 5

fun Route.transcribeRoute(
    modelRepository: ModelRepository,
    sessionRepository: SessionRepository,
    sessionCoordinator: SessionCoordinator,
    jobRepository: JobRepository,
    inferenceScheduler: InferenceScheduler,
) {
  post("/v1/sessions/{id}/transcribe") {
    val caller = call.principal<ClientPrincipal>()!!
    val sessionId = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
    val session = sessionRepository.getSession(sessionId).first()
    if (session == null || session.clientId != caller.clientId) {
      respondApiError(
          status = HttpStatusCode.NotFound,
          code = ApiErrorCode.SESSION_NOT_FOUND,
          message = "Session $sessionId does not exist",
      )
      return@post
    }

    val model = modelRepository.getModel(session.modelId).first()
    if (model == null) {
      respondApiError(
          status = HttpStatusCode.NotFound,
          code = ApiErrorCode.MODEL_NOT_FOUND,
          message = "Model ${session.modelId} does not exist",
      )
      return@post
    }
    if (ModelCapability.AUDIO !in model.definition.capabilities) {
      respondApiError(
          status = HttpStatusCode.NotImplemented,
          code = ApiErrorCode.NOT_IMPLEMENTED,
          message = "Audio transcription is not available for model ${session.modelId}.",
      )
      return@post
    }

    val activeJobs = jobRepository.countActiveByClient(caller.clientId)
    if (activeJobs >= MAX_TRANSCRIBE_JOBS_PER_CLIENT) {
      respondApiError(
          status = HttpStatusCode.TooManyRequests,
          code = ApiErrorCode.RATE_LIMITED,
          message = "Max $MAX_TRANSCRIBE_JOBS_PER_CLIENT concurrent jobs per client",
      )
      return@post
    }

    val multipart = call.receiveMultipart()
    var audioBytes: ByteArray? = null
    var mimeType: String? = null

    while (true) {
      val part = multipart.readPart() ?: break
      if (part is io.ktor.http.content.PartData.FileItem && audioBytes == null) {
        audioBytes = part.provider().readRemaining().readBytes()
        mimeType = part.headers[HttpHeaders.ContentType] ?: "audio/mpeg"
      }
      part.dispose()
    }

    val bytes = audioBytes
    val contentType = mimeType
    if (bytes == null || contentType == null) {
      respondApiError(
          status = HttpStatusCode.BadRequest,
          code = ApiErrorCode.INVALID_INPUT,
          message = "Expected multipart form data with an audio file.",
      )
      return@post
    }

    when (val validation = AudioValidator.validate(contentType, bytes.size.toLong())) {
      AudioValidator.Result.Valid -> Unit
      is AudioValidator.Result.Invalid -> {
        val code =
            when (validation.code) {
              "REQUEST_TOO_LARGE" -> ApiErrorCode.REQUEST_TOO_LARGE
              "UNSUPPORTED_FORMAT" -> ApiErrorCode.UNSUPPORTED_FORMAT
              else -> ApiErrorCode.INVALID_INPUT
            }
        val status =
            when (code) {
              ApiErrorCode.REQUEST_TOO_LARGE -> HttpStatusCode.PayloadTooLarge
              ApiErrorCode.UNSUPPORTED_FORMAT -> HttpStatusCode.UnsupportedMediaType
              else -> HttpStatusCode.BadRequest
            }
        respondApiError(status = status, code = code, message = validation.message)
        return@post
      }
    }

    // Decode any supported format (AAC, MP3, OGG, FLAC, WAV…) to PCM-16-bit mono WAV, which is
    // the format LiteRT-LM requires via Content.AudioBytes().
    val wavBytes =
        if (bytes.isWavFile()) {
          bytes
        } else {
          withContext(Dispatchers.IO) { runCatching { decodeToWav(bytes) }.getOrNull() }
        }
    if (wavBytes == null) {
      respondApiError(
          status = HttpStatusCode.UnprocessableEntity,
          code = ApiErrorCode.INVALID_INPUT,
          message = "Could not decode audio to PCM. Ensure the file is a valid audio recording.",
      )
      return@post
    }

    val jobId = UUID.randomUUID().toString()
    jobRepository.createJob(
        app.altio.service.domain.job.Job(
            id = jobId,
            sessionId = sessionId,
            clientId = caller.clientId,
            appName = session.appName,
            type = JobType.TRANSCRIBE,
            status = JobStatus.QUEUED,
            createdAt = Instant.now(),
        )
    )
    if (
        !inferenceScheduler.enqueue(
            app.altio.service.data.runtime.SchedulerWork.Transcribe(
                jobId = jobId,
                sessionId = sessionId,
                audioBytes = wavBytes,
                mimeType = "audio/wav",
            )
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
    sessionCoordinator.touchSession(sessionId)
    call.respond(
        HttpStatusCode.Accepted,
        SubmitJobResponse(jobId = jobId, status = JobStatus.QUEUED.toSdkStatus()),
    )
  }
}

private fun ByteArray.isWavFile(): Boolean =
    size >= 12 &&
        this[0] == 'R'.code.toByte() &&
        this[1] == 'I'.code.toByte() &&
        this[2] == 'F'.code.toByte() &&
        this[3] == 'F'.code.toByte() &&
        this[8] == 'W'.code.toByte() &&
        this[9] == 'A'.code.toByte() &&
        this[10] == 'V'.code.toByte() &&
        this[11] == 'E'.code.toByte()
