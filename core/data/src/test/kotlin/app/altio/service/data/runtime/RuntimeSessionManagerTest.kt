/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.runtime

import app.altio.service.domain.model.Model
import app.altio.service.domain.model.ModelCapability
import app.altio.service.domain.model.ModelDefaultConfig
import app.altio.service.domain.model.ModelDefinition
import app.altio.service.domain.model.ModelFile
import app.altio.service.domain.model.ModelSource
import app.altio.service.domain.model.ModelStatus
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class RuntimeSessionManagerTest {

  @Test
  fun `openSession stores runtime session under requested id`() = runTest {
    val sessionId = "session-api-id"
    val holder =
        RuntimeEngineHolder(
            object : RuntimeProvider {
              override fun supports(model: Model): Boolean = true

              override suspend fun load(model: Model, config: RuntimeConfig): RuntimeEngine =
                  object : RuntimeEngine {
                    override suspend fun createSession(
                        sessionId: String,
                        params: SessionParams,
                    ): RuntimeSession = StoredSessionRuntimeSession(sessionId)

                    override fun close() {}
                  }
            }
        )

    val manager = RuntimeSessionManager(holder)
    val opened =
        manager.openSession(
            sessionId = sessionId,
            model =
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
                ),
            config = RuntimeConfig(),
            params = SessionParams(),
        )

    assertSame(opened, manager.getSession(sessionId))
    assertEquals(sessionId, opened.sessionId)
  }

  @Test
  fun `engine holder reloads same model when runtime config changes`() = runTest {
    val model = readyModel()
    val createdEngines = mutableListOf<TestRuntimeEngine>()
    val holder =
        RuntimeEngineHolder(
            object : RuntimeProvider {
              override fun supports(model: Model): Boolean = true

              override suspend fun load(model: Model, config: RuntimeConfig): RuntimeEngine =
                  TestRuntimeEngine().also(createdEngines::add)
            }
        )

    val gpuEngine = holder.load(model, RuntimeConfig())
    val cpuEngine =
        holder.load(
            model,
            RuntimeConfig(accelerator = app.altio.service.domain.runtime.Accelerator.CPU),
        )

    assertNotSame(gpuEngine, cpuEngine)
    assertEquals(2, createdEngines.size)
    assertEquals(1, createdEngines.first().closeCount)
  }

  @Test
  fun `activeSessionCount tracks open and closed runtime sessions`() = runTest {
    val manager = RuntimeSessionManager(testEngineHolder())
    assertEquals(0, manager.activeSessionCount.value)

    manager.openSession(
        sessionId = "session-1",
        model = readyModel(),
        config = RuntimeConfig(),
        params = SessionParams(),
    )
    manager.openSession(
        sessionId = "session-2",
        model = readyModel(),
        config = RuntimeConfig(),
        params = SessionParams(),
    )

    assertEquals(2, manager.activeSessionCount.value)

    manager.closeSession("session-1")
    assertEquals(1, manager.activeSessionCount.value)

    manager.closeAll()
    assertEquals(0, manager.activeSessionCount.first())
  }

  @Test
  fun `loading a different runtime config invalidates live and persisted sessions`() = runTest {
    val deletedSessions = mutableListOf<String>()
    var deleteAllCount = 0
    val repository =
        object : SessionRepository {
          override fun getSession(id: String): Flow<Session?> = flowOf(null)

          override fun observeAllSessions(): Flow<List<Session>> = flowOf(emptyList())

          override fun observeSessionCount(): Flow<Int> = flowOf(0)

          override suspend fun createSession(session: Session): Session = session

          override suspend fun deleteSession(id: String) {
            deletedSessions += id
          }

          override suspend fun deleteAllSessions() {
            deleteAllCount++
          }

          override suspend fun incrementMessageCount(id: String) {}

          override suspend fun touchSession(id: String) {}

          override suspend fun resetSession(id: String) {}

          override suspend fun getStaleSessionIds(cutoff: Instant): List<String> = emptyList()

          override suspend fun countByClient(clientId: String): Int = 0
        }
    val manager = RuntimeSessionManager(testEngineHolder(), repository)
    val model = readyModel()

    manager.openSession("session-1", model, RuntimeConfig(), SessionParams())
    manager.openSession(
        "session-2",
        model,
        RuntimeConfig(accelerator = app.altio.service.domain.runtime.Accelerator.CPU),
        SessionParams(),
    )

    assertNull(manager.getSession("session-1"))
    assertEquals(1, manager.activeSessionCount.value)
    assertEquals(1, deleteAllCount)
    assertEquals(emptyList<String>(), deletedSessions)
  }
}

private class StoredSessionRuntimeSession(override val sessionId: String) : RuntimeSession {
  override fun generateStream(request: InferenceRequest): Flow<InferenceChunk> = emptyFlow()

  override suspend fun transcribe(audioBytes: ByteArray, mimeType: String): TranscriptionResult =
      throw UnsupportedOperationException()

  override suspend fun reset() {}

  override fun close() {}
}

private class TestRuntimeEngine : RuntimeEngine {
  var closeCount: Int = 0

  override suspend fun createSession(sessionId: String, params: SessionParams): RuntimeSession =
      StoredSessionRuntimeSession(sessionId)

  override fun close() {
    closeCount++
  }
}

private fun testEngineHolder(): RuntimeEngineHolder =
    RuntimeEngineHolder(
        object : RuntimeProvider {
          override fun supports(model: Model): Boolean = true

          override suspend fun load(model: Model, config: RuntimeConfig): RuntimeEngine =
              TestRuntimeEngine()
        }
    )

private fun readyModel(id: String = "model-id") =
    Model(
        definition =
            ModelDefinition(
                id = id,
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
                        accelerators = listOf("gpu", "cpu"),
                    ),
            ),
        status = ModelStatus.READY,
        filePath = "/tmp/model.bin",
        downloadedAt = null,
    )
