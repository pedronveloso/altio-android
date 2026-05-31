/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.session

import app.altio.service.data.runtime.RuntimeEngineHolder
import app.altio.service.data.runtime.RuntimeSessionManager
import app.altio.service.domain.model.Model
import app.altio.service.domain.model.ModelCapability
import app.altio.service.domain.model.ModelDefaultConfig
import app.altio.service.domain.model.ModelDefinition
import app.altio.service.domain.model.ModelFile
import app.altio.service.domain.model.ModelSource
import app.altio.service.domain.model.ModelStatus
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
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SessionCoordinatorTest {

  @Test
  fun `createSession closes runtime session when persistence fails`() = runTest {
    val repository =
        object : SessionRepository {
          override fun getSession(id: String): Flow<Session?> = flowOf(null)

          override fun observeAllSessions(): Flow<List<Session>> = flowOf(emptyList())

          override fun observeSessionCount(): Flow<Int> = flowOf(0)

          override suspend fun createSession(session: Session): Session =
              error("database write failed")

          override suspend fun deleteSession(id: String) {}

          override suspend fun deleteAllSessions() {}

          override suspend fun incrementMessageCount(id: String) {}

          override suspend fun touchSession(id: String) {}

          override suspend fun resetSession(id: String) {}

          override suspend fun getStaleSessionIds(cutoff: Instant): List<String> = emptyList()

          override suspend fun countByClient(clientId: String): Int = 0
        }
    val manager = RuntimeSessionManager(testEngineHolder(), repository)
    val coordinator = SessionCoordinator(repository, manager)
    val session = session(id = "ses-create-fail")

    val failure =
        runCatching {
              coordinator.createSession(session, readyModel(), RuntimeConfig(), SessionParams())
            }
            .exceptionOrNull()

    assertInstanceOf(IllegalStateException::class.java, failure)
    assertNull(manager.getSession(session.id))
  }

  @Test
  fun `createSession allows only one active runtime session when requests overlap`() = runTest {
    val repository =
        object : SessionRepository {
          override fun getSession(id: String): Flow<Session?> = flowOf(null)

          override fun observeAllSessions(): Flow<List<Session>> = flowOf(emptyList())

          override fun observeSessionCount(): Flow<Int> = flowOf(0)

          override suspend fun createSession(session: Session): Session = session

          override suspend fun deleteSession(id: String) {}

          override suspend fun deleteAllSessions() {}

          override suspend fun incrementMessageCount(id: String) {}

          override suspend fun touchSession(id: String) {}

          override suspend fun resetSession(id: String) {}

          override suspend fun getStaleSessionIds(cutoff: Instant): List<String> = emptyList()

          override suspend fun countByClient(clientId: String): Int = 0
        }
    val manager = RuntimeSessionManager(testEngineHolder(), repository)
    val coordinator = SessionCoordinator(repository, manager)

    val results =
        listOf("ses-race-1", "ses-race-2")
            .map { id ->
              async {
                runCatching {
                  coordinator.createSession(
                      session(id),
                      readyModel(),
                      RuntimeConfig(),
                      SessionParams(),
                  )
                }
              }
            }
            .awaitAll()

    assertEquals(1, results.count { it.isSuccess })
    assertEquals(1, results.count { it.exceptionOrNull() is ActiveRuntimeSessionException })
    assertEquals(1, manager.activeSessionCount.value)
  }

  @Test
  fun `deleteSession clears runtime and persisted session`() = runTest {
    val deletedIds = mutableListOf<String>()
    val repository =
        object : SessionRepository {
          override fun getSession(id: String): Flow<Session?> = flowOf(null)

          override fun observeAllSessions(): Flow<List<Session>> = flowOf(emptyList())

          override fun observeSessionCount(): Flow<Int> = flowOf(0)

          override suspend fun createSession(session: Session): Session = session

          override suspend fun deleteSession(id: String) {
            deletedIds += id
          }

          override suspend fun deleteAllSessions() {}

          override suspend fun incrementMessageCount(id: String) {}

          override suspend fun touchSession(id: String) {}

          override suspend fun resetSession(id: String) {}

          override suspend fun getStaleSessionIds(cutoff: Instant): List<String> = emptyList()

          override suspend fun countByClient(clientId: String): Int = 0
        }
    val manager = RuntimeSessionManager(testEngineHolder(), repository)
    val coordinator = SessionCoordinator(repository, manager)
    val session = session(id = "ses-delete")

    coordinator.createSession(session, readyModel(), RuntimeConfig(), SessionParams())
    coordinator.deleteSession(session.id)

    assertNull(manager.getSession(session.id))
    assertEquals(listOf(session.id), deletedIds)
  }
}

private fun testEngineHolder(): RuntimeEngineHolder =
    RuntimeEngineHolder(
        object : RuntimeProvider {
          override fun supports(model: Model): Boolean = true

          override suspend fun load(model: Model, config: RuntimeConfig): RuntimeEngine =
              object : RuntimeEngine {
                override suspend fun createSession(
                    sessionId: String,
                    params: SessionParams,
                ): RuntimeSession = StoredRuntimeSession(sessionId)

                override fun close() {}
              }
        }
    )

private fun readyModel() =
    Model(
        definition =
            ModelDefinition(
                id = "model-id",
                name = "Test Model",
                description = "Test",
                source = ModelSource.DOWNLOADED,
                version = "1",
                huggingfaceRepo = "repo",
                sizeBytes = 1L,
                sha256 = "hash",
                capabilities = listOf(ModelCapability.TEXT),
                minSdk = 31,
                minDeviceMemoryGb = 1,
                maxContextLength = 1024,
                runtime = "test",
                files = listOf(ModelFile("model.bin", 1L, "hash")),
                defaultConfig =
                    ModelDefaultConfig(
                        topK = 1,
                        topP = 1f,
                        temperature = 1f,
                        maxTokens = 64,
                        accelerators = listOf("cpu"),
                    ),
            ),
        status = ModelStatus.READY,
        filePath = "/tmp/model.bin",
        downloadedAt = null,
    )

private fun session(id: String) =
    Session(
        id = id,
        clientId = "client-test",
        appName = null,
        modelId = "model-id",
        systemPrompt = null,
        generationConfig = GenerationConfig(),
        messageCount = 0,
        createdAt = Instant.now(),
        lastActiveAt = Instant.now(),
    )

private class StoredRuntimeSession(override val sessionId: String) : RuntimeSession {
  override fun generateStream(request: InferenceRequest): Flow<InferenceChunk> = emptyFlow()

  override suspend fun transcribe(audioBytes: ByteArray, mimeType: String): TranscriptionResult =
      throw UnsupportedOperationException()

  override suspend fun reset() {}

  override fun close() {}
}
