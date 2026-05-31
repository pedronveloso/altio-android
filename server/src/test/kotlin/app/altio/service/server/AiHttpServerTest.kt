/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server

import app.altio.service.data.runtime.InferenceScheduler
import app.altio.service.data.runtime.RuntimeEngineHolder
import app.altio.service.data.runtime.RuntimeSessionManager
import app.altio.service.domain.job.Job
import app.altio.service.domain.job.JobRepository
import app.altio.service.domain.model.DownloadProgress
import app.altio.service.domain.model.Model
import app.altio.service.domain.model.ModelRepository
import app.altio.service.domain.model.ModelStatus
import app.altio.service.domain.runtime.RuntimeConfig
import app.altio.service.domain.runtime.RuntimeEngine
import app.altio.service.domain.runtime.RuntimeProvider
import app.altio.service.domain.session.Session
import app.altio.service.domain.session.SessionRepository
import app.altio.service.domain.settings.AppSettings
import app.altio.service.domain.settings.OnboardingCheckpoint
import app.altio.service.domain.settings.SettingsRepository
import app.altio.service.domain.token.ClientToken
import app.altio.service.domain.token.TokenRepository
import java.net.ServerSocket
import java.net.Socket
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AiHttpServerTest {

  private val deps = stubServerDependencies()
  private lateinit var server: AiHttpServer

  @BeforeEach
  fun setUp() {
    server = AiHttpServer(deps)
  }

  @AfterEach
  fun tearDown() {
    server.stop()
  }

  @Test
  fun `port is null before start`() {
    assertTrue(server.port.value == null)
  }

  @Test
  fun `port is set after start`() {
    val port = findFreePort()
    server.start(port)
    assertNotNull(server.port.value)
    assertTrue(server.port.value == port)
  }

  @Test
  fun `port is null after stop`() {
    server.start(findFreePort())
    server.stop()
    assertTrue(server.port.value == null)
  }

  @Test
  fun `server accepts connections on loopback after start`() {
    val port = findFreePort()
    server.start(port)
    // Allow Ktor's CIO engine a moment to fully bind the socket
    Thread.sleep(200)
    val boundPort = checkNotNull(server.port.value) { "Port should not be null after start" }
    Socket(AiHttpServer.LOOPBACK_HOST, boundPort).use { socket ->
      assertTrue(socket.isConnected, "Should connect to loopback address")
    }
  }

  @Test
  fun `start fails when requested port is already in use`() {
    ServerSocket(0).use { occupied ->
      val port = occupied.localPort
      val failure =
          org.junit.jupiter.api.assertThrows<AiHttpServerStartException> { server.start(port) }
      assertTrue(failure.kind == AiHttpServerStartException.Kind.PORT_IN_USE)
    }

    assertTrue(server.port.value == null)
  }

  @Test
  fun `start rejects ports below app settings minimum as invalid`() {
    val failure =
        org.junit.jupiter.api.assertThrows<AiHttpServerStartException> { server.start(80) }

    assertEquals(AiHttpServerStartException.Kind.INVALID_PORT, failure.kind)
    assertTrue(server.port.value == null)
  }
}

internal fun stubServerDependencies(): ServerDependencies {
  val modelRepository =
      object : ModelRepository {
        override fun getAvailableModels(): Flow<List<Model>> = flowOf(emptyList())

        override fun getModel(id: String): Flow<Model?> = flowOf(null)

        override fun getDownloadProgress(id: String): Flow<DownloadProgress> = emptyFlow()

        override suspend fun startDownload(id: String) {}

        override suspend fun pauseDownload(id: String) {}

        override suspend fun resumeDownload(id: String) {}

        override suspend fun cancelDownload(id: String) {}

        override suspend fun deleteModel(id: String) {}

        override suspend fun verifyModel(id: String): Boolean = false

        override suspend fun setStatus(id: String, status: ModelStatus) {}
      }
  val sessionRepository =
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
  val jobRepository =
      object : JobRepository {
        override fun getJob(id: String): Flow<Job?> = flowOf(null)

        override fun observeAllJobs(): Flow<List<Job>> = flowOf(emptyList())

        override fun observeActiveJobs(): Flow<List<Job>> = flowOf(emptyList())

        override fun observeActiveJobCount(): Flow<Int> = flowOf(0)

        override suspend fun createJob(job: Job): Job = job

        override suspend fun updateStarted(id: String, startedAt: Instant) {}

        override suspend fun complete(id: String, output: String, completedAt: Instant) {}

        override suspend fun fail(id: String, errorCode: String, completedAt: Instant) {}

        override suspend fun cancel(id: String, completedAt: Instant) {}

        override suspend fun failActiveJobsOnStartup(completedAt: Instant): Int = 0

        override suspend fun countActiveByClient(clientId: String): Int = 0
      }
  val engineHolder =
      RuntimeEngineHolder(
          object : RuntimeProvider {
            override fun supports(model: Model) = false

            override suspend fun load(model: Model, config: RuntimeConfig): RuntimeEngine =
                throw UnsupportedOperationException()
          }
      )
  val sessionManager = RuntimeSessionManager(engineHolder)
  val scheduler =
      InferenceScheduler(
          scope = CoroutineScope(Dispatchers.Default),
          sessionManager = sessionManager,
          jobRepository = jobRepository,
      )
  val tokenRepository =
      object : TokenRepository {
        override suspend fun generateToken(label: String): String = "test-token"

        override suspend fun validateToken(rawToken: String): ClientToken? =
            ClientToken("hash", "client_test", "Test", 0L, null, false)

        override suspend fun revokeToken(tokenHash: String) {}

        override fun observeTokens(): Flow<List<ClientToken>> = emptyFlow()
      }
  val settingsRepository =
      object : SettingsRepository {
        override val settings: Flow<AppSettings> = flowOf(AppSettings())

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
  return ServerDependencies(
      modelRepository = modelRepository,
      sessionRepository = sessionRepository,
      jobRepository = jobRepository,
      sessionManager = sessionManager,
      inferenceScheduler = scheduler,
      settingsRepository = settingsRepository,
      tokenRepository = tokenRepository,
      engineHolder = engineHolder,
  )
}

private fun findFreePort(): Int = ServerSocket(0).use { it.localPort }
