/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server

import app.altio.service.data.runtime.RuntimeEngineHolder
import app.altio.service.domain.model.DownloadProgress
import app.altio.service.domain.model.DownloadStatus
import app.altio.service.domain.model.Model
import app.altio.service.domain.model.ModelCapability
import app.altio.service.domain.model.ModelDefaultConfig
import app.altio.service.domain.model.ModelDefinition
import app.altio.service.domain.model.ModelFile
import app.altio.service.domain.model.ModelRepository
import app.altio.service.domain.model.ModelSource
import app.altio.service.domain.model.ModelStatus
import app.altio.service.domain.runtime.RuntimeConfig
import app.altio.service.domain.runtime.RuntimeEngine
import app.altio.service.domain.runtime.RuntimeProvider
import app.altio.service.server.plugins.configureAuth
import app.altio.service.server.plugins.configureRouting
import app.altio.service.server.plugins.configureSerialization
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelsRouteTest {

  // ─── GET /v1/models ───────────────────────────────────────────────────────

  @Test
  fun `GET v1 models returns 200`() = testApplication {
    val deps = stubServerDependencies()
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response = client.get("/v1/models") { bearerAuth("test-token") }
    assertEquals(HttpStatusCode.OK, response.status)
  }

  @Test
  fun `GET v1 models returns models array in body`() = testApplication {
    val deps = stubServerDependencies().withModel(makeModel("gemma-3n-e2b-it-int4"))
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val body = client.get("/v1/models") { bearerAuth("test-token") }.bodyAsText()
    assertTrue(body.contains("\"models\""), "Expected models array in: $body")
    assertTrue(body.contains("gemma-3n-e2b-it-int4"), "Expected model id in: $body")
  }

  @Test
  fun `GET v1 models without auth returns 401`() = testApplication {
    val deps = stubServerDependencies()
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response = client.get("/v1/models")
    assertEquals(HttpStatusCode.Unauthorized, response.status)
  }

  // ─── DELETE /v1/models/{id} ───────────────────────────────────────────────

  @Test
  fun `DELETE v1 models id returns 204 when model is not loaded`() = testApplication {
    val model = makeModel("model-delete-me")
    val deps = stubServerDependencies().withModel(model) // engine has nothing loaded

    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response = client.delete("/v1/models/model-delete-me") { bearerAuth("test-token") }
    assertEquals(HttpStatusCode.NoContent, response.status)
  }

  @Test
  fun `DELETE v1 models id returns 404 for unknown model`() = testApplication {
    val deps = stubServerDependencies() // no models in repo
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response = client.delete("/v1/models/does-not-exist") { bearerAuth("test-token") }
    assertEquals(HttpStatusCode.NotFound, response.status)
  }

  @Test
  fun `DELETE v1 models id returns 409 MODEL_IN_USE when model is loaded`() = testApplication {
    val modelId = "model-in-use"
    val model = makeModel(modelId)

    // Build a fake engine that returns a no-op RuntimeEngine so load() succeeds
    val fakeEngine =
        object : RuntimeEngine {
          override suspend fun createSession(
              sessionId: String,
              params: app.altio.service.domain.runtime.SessionParams,
          ) = throw UnsupportedOperationException()

          override fun close() {}
        }
    val loadedEngineHolder =
        RuntimeEngineHolder(
            object : RuntimeProvider {
              override fun supports(model: Model) = true

              override suspend fun load(model: Model, config: RuntimeConfig): RuntimeEngine =
                  fakeEngine
            }
        )
    // Trigger load so loadedModelId is set
    kotlinx.coroutines.runBlocking { loadedEngineHolder.load(model, RuntimeConfig()) }

    val deps = stubServerDependencies().withModel(model).copy(engineHolder = loadedEngineHolder)

    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response = client.delete("/v1/models/$modelId") { bearerAuth("test-token") }
    assertEquals(HttpStatusCode.Conflict, response.status)
    val body = response.bodyAsText()
    assertTrue(body.contains("MODEL_IN_USE"), "Expected MODEL_IN_USE in: $body")
  }

  @Test
  fun `DELETE v1 models id without auth returns 401`() = testApplication {
    val deps = stubServerDependencies().withModel(makeModel("some-model"))
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response = client.delete("/v1/models/some-model")
    assertEquals(HttpStatusCode.Unauthorized, response.status)
  }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun makeModel(id: String) =
    Model(
        definition =
            ModelDefinition(
                id = id,
                name = "Test Model",
                description = "Test",
                source = ModelSource.DOWNLOADED,
                version = "1.0",
                huggingfaceRepo = "test/repo",
                sizeBytes = 1_000_000L,
                sha256 = "abc123",
                capabilities = listOf(ModelCapability.TEXT),
                minSdk = 29,
                minDeviceMemoryGb = 4,
                maxContextLength = 4096,
                runtime = "litert",
                files =
                    listOf(
                        ModelFile(name = "model.bin", sizeBytes = 1_000_000L, sha256 = "abc123")
                    ),
                defaultConfig =
                    ModelDefaultConfig(
                        topK = 64,
                        topP = 0.95f,
                        temperature = 1.0f,
                        maxTokens = 2048,
                        accelerators = listOf("cpu"),
                    ),
            ),
        status = ModelStatus.READY,
        filePath = "/data/models/$id.bin",
        downloadedAt = System.currentTimeMillis(),
    )

private fun ServerDependencies.withModel(model: Model): ServerDependencies {
  val fakeRepo =
      object : ModelRepository by modelRepository {
        override fun getAvailableModels(): Flow<List<Model>> = flowOf(listOf(model))

        override fun getModel(id: String): Flow<Model?> =
            flowOf(if (id == model.definition.id) model else null)

        override fun getDownloadProgress(id: String): Flow<DownloadProgress> =
            flowOf(
                DownloadProgress(
                    modelId = id,
                    bytesDownloaded = 0,
                    totalBytes = 0,
                    bytesPerSec = 0,
                    etaMs = 0L,
                    status = DownloadStatus.QUEUED,
                )
            )
      }
  return copy(modelRepository = fakeRepo)
}
