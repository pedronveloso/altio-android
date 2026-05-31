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
import app.altio.service.domain.model.ModelCapability
import app.altio.service.domain.model.ModelDefaultConfig
import app.altio.service.domain.model.ModelDefinition
import app.altio.service.domain.model.ModelFile
import app.altio.service.domain.model.ModelRepository
import app.altio.service.domain.model.ModelSource
import app.altio.service.domain.model.ModelStatus
import app.altio.service.domain.runtime.InferenceChunk
import app.altio.service.domain.runtime.InferenceRequest
import app.altio.service.domain.runtime.RuntimeConfig
import app.altio.service.domain.runtime.RuntimeEngine
import app.altio.service.domain.runtime.RuntimeProvider
import app.altio.service.domain.runtime.RuntimeSession
import app.altio.service.domain.runtime.TranscriptionResult
import app.altio.service.domain.session.Session
import app.altio.service.domain.session.SessionRepository
import app.altio.service.server.plugins.configureAuth
import app.altio.service.server.plugins.configureRouting
import app.altio.service.server.plugins.configureSerialization
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranscribeRouteTest {

  @Test
  fun `POST transcribe returns 202 for audio capable session model`() = testApplication {
    val session = makeSession(modelId = "demo-model")
    val jobs = ConcurrentHashMap<String, Job>()
    val jobRepository = InMemoryJobRepository(jobs)
    val sessionManager = fakeSessionManager(session.id)
    val deps =
        stubServerDependencies()
            .copy(
                modelRepository = modelRepositoryFor(audioModel(id = "demo-model")),
                sessionRepository = sessionRepositoryFor(session),
                jobRepository = jobRepository,
                sessionManager = sessionManager,
                inferenceScheduler =
                    InferenceScheduler(
                        scope = CoroutineScope(Dispatchers.Default),
                        sessionManager = sessionManager,
                        jobRepository = jobRepository,
                    ),
            )

    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response =
        client.post("/v1/sessions/${session.id}/transcribe") {
          bearerAuth("test-token")
          setBody(
              MultiPartFormDataContent(
                  formData {
                    append(
                        "audio",
                        sampleWavBytes(),
                        Headers.build {
                          append(HttpHeaders.ContentType, ContentType.Audio.MPEG.toString())
                          append(
                              HttpHeaders.ContentDisposition,
                              """form-data; name="audio"; filename="sample.mp3"""",
                          )
                        },
                    )
                  }
              )
          )
        }

    assertEquals(HttpStatusCode.Accepted, response.status)
    assertTrue(response.bodyAsText().contains("job_id"))
  }

  @Test
  fun `POST transcribe accepts AAC in MPEG-4 container`() = testApplication {
    val session = makeSession(modelId = "demo-model")
    val jobs = ConcurrentHashMap<String, Job>()
    val jobRepository = InMemoryJobRepository(jobs)
    val sessionManager = fakeSessionManager(session.id)
    val deps =
        stubServerDependencies()
            .copy(
                modelRepository = modelRepositoryFor(audioModel(id = "demo-model")),
                sessionRepository = sessionRepositoryFor(session),
                jobRepository = jobRepository,
                sessionManager = sessionManager,
                inferenceScheduler =
                    InferenceScheduler(
                        scope = CoroutineScope(Dispatchers.Default),
                        sessionManager = sessionManager,
                        jobRepository = jobRepository,
                    ),
            )

    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response =
        client.post("/v1/sessions/${session.id}/transcribe") {
          bearerAuth("test-token")
          setBody(
              MultiPartFormDataContent(
                  formData {
                    append(
                        "audio",
                        sampleWavBytes(),
                        Headers.build {
                          append(HttpHeaders.ContentType, "audio/mp4")
                          append(
                              HttpHeaders.ContentDisposition,
                              """form-data; name="audio"; filename="recording.m4a"""",
                          )
                        },
                    )
                  }
              )
          )
        }

    assertEquals(HttpStatusCode.Accepted, response.status)
    assertTrue(response.bodyAsText().contains("job_id"))
  }

  @Test
  fun `POST transcribe returns 501 when model lacks audio capability`() = testApplication {
    val session = makeSession(modelId = "text-only-model")
    val deps =
        stubServerDependencies()
            .copy(
                modelRepository = modelRepositoryFor(textOnlyModel(id = "text-only-model")),
                sessionRepository = sessionRepositoryFor(session),
            )

    application {
      configureSerialization()
      configureAuth(deps.tokenRepository)
      configureRouting(deps)
    }

    val response =
        client.post("/v1/sessions/${session.id}/transcribe") {
          bearerAuth("test-token")
          setBody(
              MultiPartFormDataContent(
                  formData {
                    append(
                        "audio",
                        byteArrayOf(1, 2, 3),
                        Headers.build {
                          append(HttpHeaders.ContentType, ContentType.Audio.MPEG.toString())
                          append(
                              HttpHeaders.ContentDisposition,
                              """form-data; name="audio"; filename="sample.mp3"""",
                          )
                        },
                    )
                  }
              )
          )
        }

    assertEquals(HttpStatusCode.NotImplemented, response.status)
    assertTrue(response.bodyAsText().contains("NOT_IMPLEMENTED"))
  }
}

private fun makeSession(modelId: String) =
    Session(
        id = "session-transcribe-1",
        clientId = "client_test",
        appName = null,
        modelId = modelId,
        systemPrompt = null,
        generationConfig = app.altio.service.domain.runtime.GenerationConfig(),
        messageCount = 0,
        createdAt = Instant.now(),
        lastActiveAt = Instant.now(),
    )

private fun modelRepositoryFor(model: Model) =
    object : ModelRepository {
      override fun getAvailableModels(): Flow<List<Model>> = flowOf(listOf(model))

      override fun getModel(id: String): Flow<Model?> =
          flowOf(model.takeIf { it.definition.id == id })

      override fun getDownloadProgress(id: String): Flow<DownloadProgress> = emptyFlow()

      override suspend fun startDownload(id: String) {}

      override suspend fun pauseDownload(id: String) {}

      override suspend fun resumeDownload(id: String) {}

      override suspend fun cancelDownload(id: String) {}

      override suspend fun deleteModel(id: String) {}

      override suspend fun verifyModel(id: String): Boolean = true

      override suspend fun setStatus(id: String, status: ModelStatus) {}
    }

private fun sessionRepositoryFor(session: Session) =
    object : SessionRepository {
      override fun getSession(id: String): Flow<Session?> = flowOf(session.takeIf { it.id == id })

      override fun observeAllSessions(): Flow<List<Session>> = flowOf(listOf(session))

      override fun observeSessionCount(): Flow<Int> = flowOf(1)

      override suspend fun createSession(session: Session): Session = session

      override suspend fun deleteSession(id: String) {}

      override suspend fun deleteAllSessions() {}

      override suspend fun incrementMessageCount(id: String) {}

      override suspend fun touchSession(id: String) {}

      override suspend fun resetSession(id: String) {}

      override suspend fun getStaleSessionIds(cutoff: Instant): List<String> = emptyList()

      override suspend fun countByClient(clientId: String): Int = 0
    }

private fun fakeSessionManager(sessionId: String): RuntimeSessionManager {
  val holder =
      RuntimeEngineHolder(
          object : RuntimeProvider {
            override fun supports(model: Model) = false

            override suspend fun load(model: Model, config: RuntimeConfig): RuntimeEngine =
                throw UnsupportedOperationException()
          }
      )
  return object : RuntimeSessionManager(holder) {
    override fun getSession(sessionId: String): RuntimeSession? =
        if (sessionId == "session-transcribe-1") FakeTranscribeSession(sessionId) else null
  }
}

private fun audioModel(id: String) =
    makeModel(id, listOf(ModelCapability.TEXT, ModelCapability.AUDIO))

private fun textOnlyModel(id: String) = makeModel(id, listOf(ModelCapability.TEXT))

private fun makeModel(id: String, capabilities: List<ModelCapability>) =
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
                capabilities = capabilities,
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
                        maxTokens = 128,
                        accelerators = listOf("cpu"),
                    ),
            ),
        status = ModelStatus.READY,
        filePath = "/tmp/model.bin",
        downloadedAt = null,
    )

private fun sampleWavBytes(): ByteArray =
    intArrayOf(
            0x52,
            0x49,
            0x46,
            0x46, // RIFF
            0x28,
            0x00,
            0x00,
            0x00, // chunk size
            0x57,
            0x41,
            0x56,
            0x45, // WAVE
            0x66,
            0x6D,
            0x74,
            0x20, // fmt
            0x10,
            0x00,
            0x00,
            0x00, // subchunk size
            0x01,
            0x00, // PCM
            0x01,
            0x00, // mono
            0x40,
            0x1F,
            0x00,
            0x00, // 8000 Hz
            0x80,
            0x3E,
            0x00,
            0x00, // byte rate
            0x02,
            0x00, // block align
            0x10,
            0x00, // bits per sample
            0x64,
            0x61,
            0x74,
            0x61, // data
            0x04,
            0x00,
            0x00,
            0x00, // data size
            0x00,
            0x00,
            0x00,
            0x00, // two silent samples
        )
        .map(Int::toByte)
        .toByteArray()

private class InMemoryJobRepository(
    private val jobs: ConcurrentHashMap<String, Job>,
) : JobRepository {
  override fun getJob(id: String): Flow<Job?> = flowOf(jobs[id])

  override fun observeAllJobs(): Flow<List<Job>> = flowOf(jobs.values.toList())

  override fun observeActiveJobs(): Flow<List<Job>> = flowOf(jobs.values.toList())

  override fun observeActiveJobCount(): Flow<Int> = flowOf(0)

  override suspend fun createJob(job: Job): Job {
    jobs[job.id] = job
    return job
  }

  override suspend fun updateStarted(id: String, startedAt: Instant) {}

  override suspend fun complete(id: String, output: String, completedAt: Instant) {}

  override suspend fun fail(id: String, errorCode: String, completedAt: Instant) {}

  override suspend fun cancel(id: String, completedAt: Instant) {}

  override suspend fun failActiveJobsOnStartup(completedAt: Instant): Int = 0

  override suspend fun countActiveByClient(clientId: String): Int = 0
}

private class FakeTranscribeSession(
    override val sessionId: String,
) : RuntimeSession {
  override fun generateStream(request: InferenceRequest): Flow<InferenceChunk> = emptyFlow()

  override suspend fun transcribe(audioBytes: ByteArray, mimeType: String): TranscriptionResult =
      TranscriptionResult(
          transcript = "Demo transcript",
          language = "en",
          durationMs = 1000,
      )

  override suspend fun reset() {}

  override fun close() {}
}
