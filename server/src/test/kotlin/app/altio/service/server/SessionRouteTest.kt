/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server

import app.altio.service.data.runtime.RuntimeEngineHolder
import app.altio.service.data.runtime.RuntimeSessionManager
import app.altio.service.domain.model.Model
import app.altio.service.domain.model.ModelCapability
import app.altio.service.domain.model.ModelDefaultConfig
import app.altio.service.domain.model.ModelDefinition
import app.altio.service.domain.model.ModelFile
import app.altio.service.domain.model.ModelRepository
import app.altio.service.domain.model.ModelSource
import app.altio.service.domain.model.ModelStatus
import app.altio.service.domain.runtime.Accelerator
import app.altio.service.domain.runtime.GenerationConfig
import app.altio.service.domain.runtime.InferenceChunk
import app.altio.service.domain.runtime.InferenceRequest
import app.altio.service.domain.runtime.RuntimeConfig
import app.altio.service.domain.runtime.RuntimeEngine
import app.altio.service.domain.runtime.RuntimeProvider
import app.altio.service.domain.runtime.RuntimeSession
import app.altio.service.domain.runtime.SessionParams
import app.altio.service.domain.runtime.TranscriptionResult
import app.altio.service.domain.session.Session
import app.altio.service.domain.session.SessionRepository
import app.altio.service.domain.settings.AppSettings
import app.altio.service.domain.settings.OnboardingCheckpoint
import app.altio.service.domain.settings.SettingsRepository
import app.altio.service.server.plugins.configureAuth
import app.altio.service.server.plugins.configureRouting
import app.altio.service.server.plugins.configureSerialization
import io.ktor.client.request.*
import io.ktor.client.request.bearerAuth
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.ContentType
import io.ktor.server.testing.*
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionRouteTest {

  @Test
  fun `GET v1 sessions id returns session details`() = testApplication {
    val session = makeSession("ses-get-1", messageCount = 3)
    val deps = stubServerDependencies().withSession(session)

    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response = client.get("/v1/sessions/ses-get-1") { bearerAuth("test-token") }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.bodyAsText()
    assertTrue(body.contains("ses-get-1"), "Expected session_id in: $body")
    assertTrue(body.contains("gemma-3n"), "Expected model_id in: $body")
    assertTrue(body.contains("3"), "Expected message_count in: $body")
  }

  @Test
  fun `GET v1 sessions id returns 404 for unknown session`() = testApplication {
    val deps = stubServerDependencies()
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }
    val response = client.get("/v1/sessions/nonexistent") { bearerAuth("test-token") }
    assertEquals(HttpStatusCode.NotFound, response.status)
  }

  @Test
  fun `POST v1 sessions id reset resets message count`() = testApplication {
    val session = makeSession("ses-reset-1", messageCount = 5)
    val resetCount = AtomicInteger(0)
    val deps =
        stubServerDependencies().withSession(session, onReset = { resetCount.incrementAndGet() })

    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response = client.post("/v1/sessions/ses-reset-1/reset") { bearerAuth("test-token") }
    assertEquals(HttpStatusCode.NoContent, response.status)
    assertEquals(1, resetCount.get())
  }

  @Test
  fun `POST v1 sessions id reset returns 404 for unknown session`() = testApplication {
    val deps = stubServerDependencies()
    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }
    val response = client.post("/v1/sessions/nonexistent/reset") { bearerAuth("test-token") }
    assertEquals(HttpStatusCode.NotFound, response.status)
  }

  @Test
  fun `POST create then DELETE session uses one shared session id across DB and runtime`() =
      testApplication {
        val repository = InMemorySessionRepository()
        val sessionManager =
            RuntimeSessionManager(
                RuntimeEngineHolder(
                    object : RuntimeProvider {
                      override fun supports(model: Model): Boolean = true

                      override suspend fun load(
                          model: Model,
                          config: RuntimeConfig,
                      ): RuntimeEngine =
                          object : RuntimeEngine {
                            override suspend fun createSession(
                                sessionId: String,
                                params: SessionParams,
                            ): RuntimeSession = FakeRouteRuntimeSession(sessionId)

                            override fun close() {}
                          }
                    }
                )
            )
        val deps =
            stubServerDependencies()
                .copy(
                    modelRepository = ReadyModelRepository(),
                    sessionRepository = repository,
                    sessionManager = sessionManager,
                )

        application {
          configureSerialization()
          configureAuth(deps.tokenRepository)
          configureRouting(deps)
        }

        val createResponse =
            client.post("/v1/sessions") {
              bearerAuth("test-token")
              header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
              setBody("""{"model_id":"gemma-3n-e2b-it-int4"}""")
            }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val sessionId = SESSION_ID_REGEX.find(createResponse.bodyAsText())?.groupValues?.get(1)
        assertNotNull(sessionId)

        val createdId = sessionId!!
        assertNotNull(repository.sessions[createdId])
        assertNotNull(sessionManager.getSession(createdId))

        val deleteResponse = client.delete("/v1/sessions/$createdId") { bearerAuth("test-token") }
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
        assertEquals(null, repository.sessions[createdId])
        assertEquals(null, sessionManager.getSession(createdId))
      }

  @Test
  fun `POST v1 sessions returns 409 MODEL_IN_USE when a runtime session is already active`() =
      testApplication {
        val repository = InMemorySessionRepository()
        val sessionManager =
            RuntimeSessionManager(
                RuntimeEngineHolder(
                    object : RuntimeProvider {
                      override fun supports(model: Model): Boolean = true

                      override suspend fun load(
                          model: Model,
                          config: RuntimeConfig,
                      ): RuntimeEngine =
                          object : RuntimeEngine {
                            override suspend fun createSession(
                                sessionId: String,
                                params: SessionParams,
                            ): RuntimeSession = FakeRouteRuntimeSession(sessionId)

                            override fun close() {}
                          }
                    }
                )
            )
        sessionManager.openSession(
            sessionId = "active-runtime-session",
            model = readyModel(),
            config = RuntimeConfig(),
            params = SessionParams(),
        )
        val deps =
            stubServerDependencies()
                .copy(
                    modelRepository = ReadyModelRepository(),
                    sessionRepository = repository,
                    sessionManager = sessionManager,
                )

        application {
          configureSerialization()
          configureAuth(deps.tokenRepository)
          configureRouting(deps)
        }

        val createResponse =
            client.post("/v1/sessions") {
              bearerAuth("test-token")
              header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
              setBody("""{"model_id":"gemma-3n-e2b-it-int4"}""")
            }

        assertEquals(HttpStatusCode.Conflict, createResponse.status)
        val body = createResponse.bodyAsText()
        assertTrue(body.contains("MODEL_IN_USE"), "Expected MODEL_IN_USE in: $body")
        assertTrue(repository.sessions.isEmpty(), "Expected no persisted session after: $body")
      }

  @Test
  fun `POST v1 sessions resolves omitted model id from active settings model`() = testApplication {
    val repository = InMemorySessionRepository()
    val sessionManager =
        RuntimeSessionManager(
            RuntimeEngineHolder(
                object : RuntimeProvider {
                  override fun supports(model: Model): Boolean = true

                  override suspend fun load(
                      model: Model,
                      config: RuntimeConfig,
                  ): RuntimeEngine =
                      object : RuntimeEngine {
                        override suspend fun createSession(
                            sessionId: String,
                            params: SessionParams,
                        ): RuntimeSession = FakeRouteRuntimeSession(sessionId)

                        override fun close() {}
                      }
                }
            )
        )
    val deps =
        stubServerDependencies()
            .copy(
                modelRepository =
                    ReadyModelRepository(
                        models =
                            mapOf(
                                "demo-model" to readyModel("demo-model"),
                                "gemma-4-e2b-it" to readyModel("gemma-4-e2b-it"),
                            )
                    ),
                sessionRepository = repository,
                sessionManager = sessionManager,
                settingsRepository = FakeSettingsRepository(activeModelId = "gemma-4-e2b-it"),
            )

    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val createResponse =
        client.post("/v1/sessions") {
          bearerAuth("test-token")
          header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
          setBody("""{}""")
        }

    assertEquals(HttpStatusCode.Created, createResponse.status)
    val sessionId = SESSION_ID_REGEX.find(createResponse.bodyAsText())?.groupValues?.get(1)
    assertNotNull(sessionId)
    assertEquals("gemma-4-e2b-it", repository.sessions[sessionId]!!.modelId)
  }

  @Test
  fun `POST v1 sessions opens runtime with resolved accelerator and max tokens from settings`() =
      testApplication {
        val capturedConfig = AtomicReference<RuntimeConfig?>()
        val repository = InMemorySessionRepository()
        val sessionManager =
            RuntimeSessionManager(
                RuntimeEngineHolder(
                    object : RuntimeProvider {
                      override fun supports(model: Model): Boolean = true

                      override suspend fun load(
                          model: Model,
                          config: RuntimeConfig,
                      ): RuntimeEngine {
                        capturedConfig.set(config)
                        return object : RuntimeEngine {
                          override suspend fun createSession(
                              sessionId: String,
                              params: SessionParams,
                          ): RuntimeSession = FakeRouteRuntimeSession(sessionId)

                          override fun close() {}
                        }
                      }
                    }
                )
            )
        val deps =
            stubServerDependencies()
                .copy(
                    modelRepository =
                        ReadyModelRepository(
                            models =
                                mapOf(
                                    "gemma-4-e2b-it" to
                                        readyModelWithAccelerators(
                                            "gemma-4-e2b-it",
                                            listOf("gpu", "cpu"),
                                        )
                                )
                        ),
                    sessionRepository = repository,
                    sessionManager = sessionManager,
                    settingsRepository =
                        FakeSettingsRepository(
                            activeModelId = "gemma-4-e2b-it",
                            accelerator = "CPU",
                            maxTokens = 2048,
                        ),
                )

        application {
          configureSerialization()
          configureAuth(deps.tokenRepository)
          configureRouting(deps)
        }

        val createResponse =
            client.post("/v1/sessions") {
              bearerAuth("test-token")
              header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
              setBody("""{}""")
            }

        assertEquals(HttpStatusCode.Created, createResponse.status)
        assertEquals(
            RuntimeConfig(accelerator = Accelerator.CPU, maxTokens = 2048),
            capturedConfig.get(),
        )
      }
}

private fun makeSession(id: String, messageCount: Int = 0) =
    Session(
        id = id,
        clientId = "client_test",
        appName = null,
        modelId = "gemma-3n",
        systemPrompt = null,
        generationConfig = GenerationConfig(),
        messageCount = messageCount,
        createdAt = Instant.now(),
        lastActiveAt = Instant.now(),
    )

private fun ServerDependencies.withSession(
    session: Session,
    onReset: () -> Unit = {},
): ServerDependencies {
  val fakeRepo =
      object : SessionRepository {
        override fun getSession(id: String): Flow<Session?> =
            flowOf(if (id == session.id) session else null)

        override fun observeAllSessions(): Flow<List<Session>> = flowOf(listOf(session))

        override fun observeSessionCount(): Flow<Int> = flowOf(1)

        override suspend fun createSession(session: Session): Session = session

        override suspend fun deleteSession(id: String) {}

        override suspend fun deleteAllSessions() {}

        override suspend fun incrementMessageCount(id: String) {}

        override suspend fun touchSession(id: String) {}

        override suspend fun resetSession(id: String) {
          onReset()
        }

        override suspend fun getStaleSessionIds(cutoff: java.time.Instant): List<String> =
            emptyList()

        override suspend fun countByClient(clientId: String): Int = 0
      }
  return copy(sessionRepository = fakeRepo)
}

private class InMemorySessionRepository : SessionRepository {
  val sessions = ConcurrentHashMap<String, Session>()
  private val state = MutableStateFlow<Map<String, Session>>(emptyMap())

  override fun getSession(id: String): Flow<Session?> = state.map { it[id] }

  override fun observeAllSessions(): Flow<List<Session>> =
      state.map { sessions -> sessions.values.sortedByDescending(Session::lastActiveAt) }

  override fun observeSessionCount(): Flow<Int> = state.map { it.size }

  override suspend fun createSession(session: Session): Session {
    sessions[session.id] = session
    state.value = sessions.toMap()
    return session
  }

  override suspend fun deleteSession(id: String) {
    sessions.remove(id)
    state.value = sessions.toMap()
  }

  override suspend fun deleteAllSessions() {
    sessions.clear()
    state.value = emptyMap()
  }

  override suspend fun incrementMessageCount(id: String) {}

  override suspend fun touchSession(id: String) {}

  override suspend fun resetSession(id: String) {}

  override suspend fun getStaleSessionIds(cutoff: Instant): List<String> = emptyList()

  override suspend fun countByClient(clientId: String): Int =
      sessions.values.count { it.clientId == clientId }
}

private class ReadyModelRepository(
    private val models: Map<String, Model> = mapOf("gemma-3n-e2b-it-int4" to readyModel()),
) : ModelRepository {
  override fun getAvailableModels(): Flow<List<Model>> = flowOf(models.values.toList())

  override fun getModel(id: String): Flow<Model?> = flowOf(models[id])

  override fun getDownloadProgress(id: String) =
      emptyFlow<app.altio.service.domain.model.DownloadProgress>()

  override suspend fun startDownload(id: String) {}

  override suspend fun pauseDownload(id: String) {}

  override suspend fun resumeDownload(id: String) {}

  override suspend fun cancelDownload(id: String) {}

  override suspend fun deleteModel(id: String) {}

  override suspend fun verifyModel(id: String): Boolean = true

  override suspend fun setStatus(id: String, status: ModelStatus) {}
}

private class FakeSettingsRepository(
    activeModelId: String = "demo-model",
    accelerator: String = "Auto",
    maxTokens: Int = 2048,
) : SettingsRepository {
  override val settings: Flow<AppSettings> =
      flowOf(
          AppSettings(
              activeModelId = activeModelId,
              accelerator = accelerator,
              maxTokens = maxTokens,
          )
      )

  override suspend fun setSetupComplete(complete: Boolean) {}

  override suspend fun setOnboardingCheckpoint(checkpoint: OnboardingCheckpoint) {}

  override suspend fun setBatteryOptimizationGuidanceSeen(seen: Boolean) {}

  override suspend fun setOemGuidanceSeen(seen: Boolean) {}

  override suspend fun setActiveModelId(modelId: String) {}

  override suspend fun setIdleShutdownMinutes(minutes: Int?) {}

  override suspend fun setStartOnBoot(enabled: Boolean) {}

  override suspend fun setServerPort(port: Int) {}

  override suspend fun setAccelerator(accelerator: String) {}

  override suspend fun setModelAccelerator(modelId: String, accelerator: String?) {}

  override suspend fun setMaxTokens(tokens: Int) {}
}

private fun readyModel(id: String = "gemma-3n-e2b-it-int4") =
    readyModelWithAccelerators(id = id, accelerators = listOf("cpu"))

private fun readyModelWithAccelerators(id: String, accelerators: List<String>) =
    Model(
        definition =
            ModelDefinition(
                id = id,
                name = "Test Model",
                description = "test",
                source = ModelSource.DOWNLOADED,
                version = "1",
                huggingfaceRepo = "repo",
                sizeBytes = 1L,
                sha256 = "hash",
                capabilities = listOf(ModelCapability.TEXT),
                minSdk = 31,
                minDeviceMemoryGb = 4,
                maxContextLength = 2048,
                runtime = "test",
                files = listOf(ModelFile("model.bin", 1L, "hash")),
                defaultConfig =
                    ModelDefaultConfig(
                        topK = 1,
                        topP = 1f,
                        temperature = 1f,
                        maxTokens = 128,
                        accelerators = accelerators,
                    ),
            ),
        status = ModelStatus.READY,
        filePath = "/tmp/model.bin",
        downloadedAt = null,
    )

private class FakeRouteRuntimeSession(override val sessionId: String) : RuntimeSession {
  override fun generateStream(request: InferenceRequest): Flow<InferenceChunk> = emptyFlow()

  override suspend fun transcribe(audioBytes: ByteArray, mimeType: String): TranscriptionResult =
      throw UnsupportedOperationException()

  override suspend fun reset() {}

  override fun close() {}
}

private val SESSION_ID_REGEX = """"session_id":"([^"]+)"""".toRegex()
