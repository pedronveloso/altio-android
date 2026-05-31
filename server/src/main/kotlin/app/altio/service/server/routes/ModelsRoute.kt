/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server.routes

import app.altio.sdk.contract.error.ApiErrorCode
import app.altio.sdk.contract.model.DownloadJobResponse
import app.altio.sdk.contract.model.LoadModelResponse
import app.altio.sdk.contract.model.ModelsListResponse
import app.altio.service.data.runtime.RuntimeEngineHolder
import app.altio.service.domain.model.ModelRepository
import app.altio.service.domain.model.ModelStatus
import app.altio.service.server.respondApiError
import app.altio.service.server.toDownloadProgressResponse
import app.altio.service.server.toModelResponse
import app.altio.service.server.toSdkStatus
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.first

fun Route.modelsRoute(modelRepository: ModelRepository, engineHolder: RuntimeEngineHolder) {
  get("/v1/models") {
    val models = modelRepository.getAvailableModels().first()
    val response = ModelsListResponse(models = models.map { it.toModelResponse() })
    call.respond(response)
  }

  post("/v1/models/{id}/download") {
    val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
    modelRepository.startDownload(id)
    call.respond(
        HttpStatusCode.Accepted,
        DownloadJobResponse(message = "Download started", modelId = id),
    )
  }

  get("/v1/models/{id}/download-progress") {
    val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
    val progress = modelRepository.getDownloadProgress(id).first()
    call.respond(progress.toDownloadProgressResponse())
  }

  delete("/v1/models/{id}") {
    val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
    val model = modelRepository.getModel(id).first()
    if (model == null) {
      respondApiError(
          status = HttpStatusCode.NotFound,
          code = ApiErrorCode.MODEL_NOT_FOUND,
          message = "Model $id does not exist",
      )
      return@delete
    }
    if (engineHolder.loadedModelId == id) {
      respondApiError(
          status = HttpStatusCode.Conflict,
          code = ApiErrorCode.MODEL_IN_USE,
          message = "Cannot delete '$id' while it is loaded. Close all sessions first.",
      )
      return@delete
    }
    modelRepository.deleteModel(id)
    call.respond(HttpStatusCode.NoContent)
  }

  post("/v1/models/{id}/load") {
    val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
    val model = modelRepository.getModel(id).first()
    if (model == null) {
      respondApiError(
          status = HttpStatusCode.NotFound,
          code = ApiErrorCode.MODEL_NOT_FOUND,
          message = "Model $id does not exist",
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
    modelRepository.setStatus(id, ModelStatus.LOADING)
    // Actual engine loading is deferred; clients should create a session to trigger it.
    modelRepository.setStatus(id, ModelStatus.LOADED)
    call.respond(
        HttpStatusCode.OK,
        LoadModelResponse(modelId = id, status = ModelStatus.LOADED.toSdkStatus()),
    )
  }
}
