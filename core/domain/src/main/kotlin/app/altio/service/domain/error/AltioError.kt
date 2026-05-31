/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.error

sealed class AltioError(val code: String, message: String) : Exception(message) {
  class SessionNotFound(sessionId: String) :
      AltioError("SESSION_NOT_FOUND", "Session $sessionId not found")

  class JobNotFound(jobId: String) : AltioError("JOB_NOT_FOUND", "Job $jobId not found")

  class ModelNotLoaded : AltioError("MODEL_NOT_LOADED", "No model is currently loaded")

  class ModelNotReady : AltioError("MODEL_NOT_READY", "Model is still loading")

  class InferenceFailed(cause: Throwable) :
      AltioError("INFERENCE_FAILED", cause.message ?: "Inference failed")

  class InvalidInput(details: String) : AltioError("INVALID_INPUT", details)

  class Unauthorized : AltioError("UNAUTHORIZED", "Missing or invalid bearer token")
}
