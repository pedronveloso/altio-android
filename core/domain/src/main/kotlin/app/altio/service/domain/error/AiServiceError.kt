/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.error

sealed class AiServiceError(val code: String, message: String) : Exception(message) {
  class SessionNotFound(sessionId: String) :
      AiServiceError("SESSION_NOT_FOUND", "Session $sessionId not found")

  class JobNotFound(jobId: String) : AiServiceError("JOB_NOT_FOUND", "Job $jobId not found")

  class ModelNotLoaded : AiServiceError("MODEL_NOT_LOADED", "No model is currently loaded")

  class ModelNotReady : AiServiceError("MODEL_NOT_READY", "Model is still loading")

  class InferenceFailed(cause: Throwable) :
      AiServiceError("INFERENCE_FAILED", cause.message ?: "Inference failed")

  class InvalidInput(details: String) : AiServiceError("INVALID_INPUT", details)

  class Unauthorized : AiServiceError("UNAUTHORIZED", "Missing or invalid bearer token")
}
