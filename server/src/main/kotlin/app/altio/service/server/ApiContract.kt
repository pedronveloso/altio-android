/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server

import app.altio.sdk.contract.error.ApiError
import app.altio.sdk.contract.error.ApiErrorCode
import app.altio.sdk.contract.error.ApiErrorEnvelope
import app.altio.sdk.contract.job.JobStatus
import app.altio.sdk.contract.job.JobStatusResponse
import app.altio.sdk.contract.model.DownloadProgressResponse
import app.altio.sdk.contract.model.DownloadStatus
import app.altio.sdk.contract.model.ModelCapability
import app.altio.sdk.contract.model.ModelResponse
import app.altio.sdk.contract.model.ModelSource
import app.altio.sdk.contract.model.ModelStatus
import app.altio.sdk.contract.session.SessionDetailResponse
import app.altio.sdk.contract.session.SessionResponse
import app.altio.sdk.contract.stream.FinishReason
import app.altio.service.domain.job.Job
import app.altio.service.domain.job.JobStatus as DomainJobStatus
import app.altio.service.domain.model.DownloadProgress
import app.altio.service.domain.model.DownloadStatus as DomainDownloadStatus
import app.altio.service.domain.model.Model
import app.altio.service.domain.model.ModelCapability as DomainModelCapability
import app.altio.service.domain.model.ModelSource as DomainModelSource
import app.altio.service.domain.model.ModelStatus as DomainModelStatus
import app.altio.service.domain.runtime.FinishReason as DomainFinishReason
import app.altio.service.domain.session.Session
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

fun Session.toSessionResponse(): SessionResponse =
    SessionResponse(
        sessionId = id,
        modelId = modelId,
        clientId = clientId,
        appName = appName,
        createdAt = createdAt.toEpochMilli(),
    )

fun Session.toSessionDetailResponse(): SessionDetailResponse =
    SessionDetailResponse(
        sessionId = id,
        modelId = modelId,
        clientId = clientId,
        appName = appName,
        systemPrompt = systemPrompt,
        messageCount = messageCount,
        createdAt = createdAt.toEpochMilli(),
        lastActiveAt = lastActiveAt.toEpochMilli(),
    )

fun Job.toJobStatusResponse(): JobStatusResponse =
    JobStatusResponse(
        jobId = id,
        sessionId = sessionId,
        appName = appName,
        status = status.toSdkStatus(),
        output = output,
        errorCode = errorCode,
        createdAt = createdAt.toEpochMilli(),
        startedAt = startedAt?.toEpochMilli(),
        completedAt = completedAt?.toEpochMilli(),
    )

fun Model.toModelResponse(): ModelResponse =
    ModelResponse(
        id = definition.id,
        name = definition.name,
        description = definition.description,
        source = definition.source.toSdkSource(),
        status = status.toSdkStatus(),
        sizeBytes = definition.sizeBytes,
        capabilities = definition.capabilities.map { it.toSdkCapability() },
        filePath = filePath,
        downloadedAt = downloadedAt,
    )

fun DownloadProgress.toDownloadProgressResponse(): DownloadProgressResponse =
    DownloadProgressResponse(
        modelId = modelId,
        bytesDownloaded = bytesDownloaded,
        totalBytes = totalBytes,
        bytesPerSec = bytesPerSec,
        etaMs = etaMs,
        fraction = fraction,
        status = status.toSdkStatus(),
    )

fun DomainJobStatus.toSdkStatus(): JobStatus =
    when (this) {
      DomainJobStatus.QUEUED -> JobStatus.QUEUED
      DomainJobStatus.RUNNING -> JobStatus.RUNNING
      DomainJobStatus.COMPLETED -> JobStatus.COMPLETED
      DomainJobStatus.FAILED -> JobStatus.FAILED
      DomainJobStatus.CANCELLED -> JobStatus.CANCELLED
    }

fun DomainModelStatus.toSdkStatus(): ModelStatus =
    when (this) {
      DomainModelStatus.NOT_DOWNLOADED -> ModelStatus.NOT_DOWNLOADED
      DomainModelStatus.DOWNLOADING -> ModelStatus.DOWNLOADING
      DomainModelStatus.PAUSED -> ModelStatus.PAUSED
      DomainModelStatus.VERIFYING -> ModelStatus.VERIFYING
      DomainModelStatus.READY -> ModelStatus.READY
      DomainModelStatus.LOADING -> ModelStatus.LOADING
      DomainModelStatus.LOADED -> ModelStatus.LOADED
    }

fun DomainDownloadStatus.toSdkStatus(): DownloadStatus =
    when (this) {
      DomainDownloadStatus.QUEUED -> DownloadStatus.QUEUED
      DomainDownloadStatus.DOWNLOADING -> DownloadStatus.DOWNLOADING
      DomainDownloadStatus.PAUSED -> DownloadStatus.PAUSED
      DomainDownloadStatus.VERIFYING -> DownloadStatus.VERIFYING
      DomainDownloadStatus.SUCCESS -> DownloadStatus.SUCCESS
      DomainDownloadStatus.FAILED -> DownloadStatus.FAILED
      DomainDownloadStatus.CANCELLED -> DownloadStatus.CANCELLED
    }

fun DomainModelCapability.toSdkCapability(): ModelCapability =
    when (this) {
      DomainModelCapability.TEXT -> ModelCapability.TEXT
      DomainModelCapability.VISION -> ModelCapability.VISION
      DomainModelCapability.AUDIO -> ModelCapability.AUDIO
      DomainModelCapability.THINKING -> ModelCapability.THINKING
    }

fun DomainModelSource.toSdkSource(): ModelSource =
    when (this) {
      DomainModelSource.DOWNLOADED -> ModelSource.DOWNLOADED
      DomainModelSource.BUILT_IN -> ModelSource.BUILT_IN
    }

fun DomainFinishReason.toSdkFinishReason(): FinishReason =
    when (this) {
      DomainFinishReason.STOP -> FinishReason.STOP
      DomainFinishReason.MAX_TOKENS -> FinishReason.MAX_TOKENS
      DomainFinishReason.CANCELLED -> FinishReason.CANCELLED
      DomainFinishReason.ERROR -> FinishReason.ERROR
    }

suspend fun RoutingContext.respondApiError(
    status: HttpStatusCode,
    code: ApiErrorCode,
    message: String,
) {
  call.respond(status, ApiErrorEnvelope(ApiError(code = code, message = message)))
}
