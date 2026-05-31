# 09 — Testing Strategy

## Principles

- Every public class in `:core:domain` and `:core:data` has unit tests
- HTTP API has integration tests using Ktor `testApplication` (no emulator)
- Runtime tests are instrumented (require hardware with LiteRT-LM support)
- UI tests cover the setup flow and critical user paths
- The demo app is the manual end-to-end test harness

---

## Test Pyramid

```
        ╔══════════════════╗
        ║   E2E / Manual   ║  :demo app on real device
        ╠══════════════════╣
        ║  Instrumented    ║  :runtime:litert, :ui Compose tests
        ╠══════════════════╣
        ║  Integration     ║  :server Ktor testApplication
        ╠══════════════════╣
        ║  Unit            ║  :core:domain, :core:data (JVM only)
        ╚══════════════════╝
```

---

## Dependencies by Test Layer

```toml
# Unit & integration (JVM)
junit5-api       = "org.junit.jupiter:junit-jupiter-api:5.11.4"
junit5-engine    = "org.junit.jupiter:junit-jupiter-engine:5.11.4"
mockk            = "io.mockk:mockk:1.14.2"
turbine          = "app.cash.turbine:turbine:1.2.0"
coroutines-test  = "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2"
ktor-test        = "io.ktor:ktor-server-test-host:3.1.3"
room-testing     = "androidx.room:room-testing:2.7.1"

# Instrumented
compose-test     = "androidx.compose.ui:ui-test-junit4"
work-testing     = "androidx.work:work-testing:2.10.0"
```

Configure JUnit 5 on Android (unit tests):
```kotlin
// in each module's build.gradle.kts
tasks.withType<Test> {
    useJUnitPlatform()
}
```

---

## `:core:domain` — Unit Tests

Pure Kotlin, no mocking framework needed for most.

### `InferenceRequestTest`
```kotlin
@Test fun `request with no messages is invalid`() {
    assertThrows<IllegalArgumentException> {
        InferenceRequest(messages = emptyList())
    }
}
```

### `JobStatusTransitionTest`
```kotlin
@Test fun `job can only be cancelled when QUEUED or RUNNING`() {
    assertFalse(JobStatus.COMPLETED.isCancellable())
    assertTrue(JobStatus.RUNNING.isCancellable())
}
```

### `FakeRuntimeSession` (shared test double)
Defined in `testFixtures` source set, shared across `:core:domain` and `:server` tests:

```kotlin
class FakeRuntimeSession(
    override val sessionId: String = "fake",
    var tokensToEmit: List<String> = listOf("Hello", " world"),
    var transcriptToReturn: String = "fake transcript",
    var errorAfterTokens: Int = Int.MAX_VALUE,
) : RuntimeSession {
    override fun generateStream(request: InferenceRequest): Flow<InferenceChunk> = flow {
        tokensToEmit.take(errorAfterTokens).forEachIndexed { i, t ->
            emit(InferenceChunk.Token(t, i))
            delay(10)
        }
        if (errorAfterTokens <= tokensToEmit.size) {
            emit(InferenceChunk.Error(RuntimeException("simulated error")))
        } else {
            emit(InferenceChunk.Done(FinishReason.STOP, TokenUsage(5, tokensToEmit.size)))
        }
    }
    override suspend fun transcribe(audioBytes: ByteArray, mimeType: String) =
        TranscriptionResult(transcriptToReturn, "en", 1000L)
    override suspend fun reset() {}
    override fun close() {}
}
```

---

## `:core:data` — Unit Tests

Use Room's in-memory database and `WorkManagerTestInitHelper`.

### `SessionDaoTest`
```kotlin
@Test fun `createSession then getById returns the session`() = runTest {
    val db = Room.inMemoryDatabaseBuilder(context, AltioDatabase::class.java).build()
    val dao = db.sessionDao()
    val entity = SessionEntity(id = "ses_1", clientId = "c1", ...)
    dao.insert(entity)
    val result = dao.getById("ses_1").first()
    assertEquals("ses_1", result?.id)
}
```

### `ModelDownloadWorkerTest`
```kotlin
@Test fun `worker writes progress and renames file on success`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    WorkManagerTestInitHelper.initializeTestWorkManager(context)
    val mockServer = MockWebServer().apply {
        enqueue(MockResponse().setBody(fakeModelBytes).setResponseCode(200))
        start()
    }
    val worker = TestListenableWorkerBuilder<ModelDownloadWorker>(context)
        .setInputData(workDataOf("model_id" to "gemma-3n-e2b-it-int4", "url" to mockServer.url("/").toString()))
        .build()
    val result = worker.doWork()
    assertEquals(ListenableWorker.Result.success(), result)
    assertTrue(File(expectedFilePath).exists())
}
```

### `InferenceSchedulerTest`
```kotlin
@Test fun `second job waits for first to complete`() = runTest {
    val scheduler = InferenceScheduler(fakeEngineHolder, fakeSessionRepo, fakeJobRepo, this)
    val job1Tokens = mutableListOf<String>()
    val job2Tokens = mutableListOf<String>()

    val flow1 = scheduler.submitJob("job1", "ses1", fakeRequest())
    val flow2 = scheduler.submitJob("job2", "ses2", fakeRequest())

    launch { flow1.collect { if (it is InferenceChunk.Token) job1Tokens += it.text } }
    launch { flow2.collect { if (it is InferenceChunk.Token) job2Tokens += it.text } }

    // job2 should only start after job1 is done
    advanceUntilIdle()
    assertFalse(job1Tokens.isEmpty())
    assertFalse(job2Tokens.isEmpty())
}
```

---

## `:server` — Integration Tests

Run on JVM, no Android device. Use `testApplication` from `ktor-server-test-host`.

### Test Setup
```kotlin
fun buildTestApp(
    sessionRepo: SessionRepository = FakeSessionRepository(),
    jobRepo: JobRepository = FakeJobRepository(),
    scheduler: InferenceScheduler = FakeInferenceScheduler(),
    tokenRepo: TokenRepository = FakeTokenRepository(validToken = "test-token"),
): TestApplicationBuilder.() -> Unit = {
    application {
        installPlugins()
        configureRoutes(sessionRepo, jobRepo, scheduler, tokenRepo)
    }
}
```

### `HealthRouteTest`
```kotlin
@Test fun `GET health returns 200 with ok status`() = testApplication {
    buildTestApp().invoke(this)
    val r = client.get("/v1/health")
    assertEquals(200, r.status.value)
    assertEquals("ok", r.body<HealthResponse>().status)
}
```

### `GenerateRouteTest`
```kotlin
@Test fun `POST generate returns 202 with job id`() = testApplication {
    buildTestApp().invoke(this)
    val r = client.post("/v1/sessions/ses_1/generate") {
        bearerAuth("test-token")
        contentType(ContentType.Application.Json)
        setBody(GenerateRequest(messages = listOf(MessageDto(role = "user", content = "Hi"))))
    }
    assertEquals(202, r.status.value)
    val body = r.body<JobResponse>()
    assertTrue(body.jobId.startsWith("job_"))
}

@Test fun `POST generate returns 401 without token`() = testApplication {
    buildTestApp().invoke(this)
    val r = client.post("/v1/sessions/ses_1/generate") {
        contentType(ContentType.Application.Json)
        setBody(GenerateRequest(messages = listOf(MessageDto(role = "user", content = "Hi"))))
    }
    assertEquals(401, r.status.value)
}
```

### `SseStreamTest`
```kotlin
@Test fun `GET job stream emits token events then done`() = testApplication {
    val fakeScheduler = FakeInferenceScheduler(tokens = listOf("Hello", " world"))
    buildTestApp(scheduler = fakeScheduler).invoke(this)

    // Submit a job first
    client.post("/v1/sessions/ses_1/generate") { ... }

    // Read SSE stream
    val events = mutableListOf<String>()
    client.get("/v1/jobs/job_1/stream") {
        bearerAuth("test-token")
        accept(ContentType.Text.EventStream)
    }.bodyAsChannel().readEvents { event ->
        events += event.event ?: ""
    }
    assertTrue(events.contains("token"))
    assertTrue(events.last() == "done")
}
```

---

## `:runtime:litert` — Instrumented Tests

These require a real Android device with API 31+ and GPU/CPU capable of running LiteRT-LM.

### `LiteRtSessionTest`
```kotlin
@Test fun generateStream_emitsTokens() = runTest(timeout = 60.seconds) {
    val modelPath = InstrumentationRegistry.getInstrumentation()
        .context.getExternalFilesDir("models")!!
        .resolve("gemma-3n-e2b-it-int4/1/gemma-3n-e2b-it-int4.task")
        .absolutePath

    assumeTrue("Model file must exist for this test", File(modelPath).exists())

    val provider = LiteRtRuntimeProvider()
    val engine = provider.load(modelPath, RuntimeConfig())
    val session = engine.createSession(SessionParams())

    val tokens = mutableListOf<String>()
    session.generateStream(InferenceRequest(
        messages = listOf(Message(Role.USER, listOf(Part.Text("Say hello"))))
    )).collect { chunk ->
        if (chunk is InferenceChunk.Token) tokens += chunk.text
    }

    assertTrue("Should emit at least one token", tokens.isNotEmpty())
    engine.close()
}
```

---

## `:ui` — Compose Tests

```kotlin
@Test fun modelDownloadScreen_showsDownloadButton_whenNotDownloaded() {
    composeTestRule.setContent {
        ModelDownloadScreen(state = DownloadState.Idle, onDownload = {}, onFinish = {})
    }
    composeTestRule.onNodeWithText("Download").assertIsDisplayed()
    composeTestRule.onNodeWithText("Finish Setup").assertDoesNotExist()
}

@Test fun tokenManagerScreen_showsCopyButton_afterGenerate() {
    composeTestRule.setContent {
        TokenManagerScreen(viewModel = fakeTokenViewModel)
    }
    composeTestRule.onNodeWithText("Generate Token").performClick()
    composeTestRule.onNodeWithContentDescription("Copy token").assertIsDisplayed()
}
```

---

## `:demo` App

The demo app acts as the manual E2E harness and a reference implementation for client integrations.

### Screens

1. **ConfigScreen** — input server port and bearer token; save to local DataStore
2. **HealthScreen** — `GET /v1/health` with raw JSON display
3. **ModelsScreen** — list models, trigger download, watch progress via SSE
4. **ChatScreen** — create session → send message → watch streaming tokens in real time
5. **AudioScreen** — pick MP3 from device → upload → show transcript
6. **JobsScreen** — list recent jobs, poll status, cancel running jobs
7. **LogScreen** — raw HTTP request/response log (OkHttp `HttpLoggingInterceptor`)

### Client Library (inside `:demo`)

```kotlin
class AiServiceClient(
    private val baseUrl: String,
    private val token: String,
    private val http: OkHttpClient,
) {
    suspend fun health(): HealthResponse
    suspend fun createSession(request: CreateSessionRequest): SessionResponse
    suspend fun generate(sessionId: String, request: GenerateRequest): JobResponse
    fun streamJob(jobId: String): Flow<SseEvent>
    suspend fun transcribe(sessionId: String, audioFile: File): JobResponse
    suspend fun cancelJob(jobId: String): JobResponse
}
```

This client can later be extracted into a `:client` library module for third-party use.
