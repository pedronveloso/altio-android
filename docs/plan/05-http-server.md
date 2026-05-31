# 05 — HTTP Server

## Technology

**Ktor Server with CIO engine** embedded directly in the Android process.

- Kotlin-native, coroutine-first
- CIO (Coroutine-based I/O) engine has no native dependencies — works on Android without special configuration
- Supports SSE, multipart, content negotiation, and routing out of the box
- `ktor-server-test-host` enables in-process testing without a real socket

---

## `AiHttpServer`

Lives in `:server`. Lifecycle-aware wrapper around the Ktor `EmbeddedServer`.

```kotlin
class AiHttpServer @Inject constructor(
    private val router: AiRouter,
    private val settingsRepository: SettingsRepository,
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val _port = MutableStateFlow<Int?>(null)
    val port: StateFlow<Int?> = _port.asStateFlow()

    fun start() {
        val port = findFreePort()
        server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            installPlugins()
            router.registerRoutes(this)
        }.also { it.start(wait = false) }
        _port.value = port
        settingsRepository.saveServerPort(port)
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 2000)
        server = null
        _port.value = null
    }

    private fun findFreePort(): Int {
        ServerSocket(0).use { return it.localPort }
    }
}
```

### Binding constraint
`host = "127.0.0.1"` — never `"0.0.0.0"`. Enforced in code, verified by a unit test that asserts the resolved bind address is loopback.

---

## Plugin Stack

```kotlin
fun Application.installPlugins() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        })
    }
    install(SSE)
    install(StatusPages) {
        exception<AiServiceException> { call, ex ->
            call.respond(HttpStatusCode.fromValue(ex.httpStatus), ErrorResponse(ex.toErrorBody()))
        }
        exception<Throwable> { call, ex ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("INTERNAL_ERROR", ex.message ?: "Unknown"))
        }
    }
    install(RequestValidation)
    install(DoubleReceive)  // allows body to be read multiple times for validation
}
```

---

## Route Structure

```kotlin
class AiRouter @Inject constructor(
    private val healthHandler: HealthHandler,
    private val modelHandler: ModelHandler,
    private val sessionHandler: SessionHandler,
    private val jobHandler: JobHandler,
    private val generateHandler: GenerateHandler,
    private val transcribeHandler: TranscribeHandler,
    private val diagnosticsHandler: DiagnosticsHandler,
    private val authMiddleware: AuthMiddleware,
) {
    fun registerRoutes(app: Application) = app.routing {
        // No auth
        get("/v1/health") { healthHandler.handle(call) }

        // Auth required for everything else
        authenticate("bearer") {
            route("/v1") {
                // Models
                get("/models")                               { modelHandler.list(call) }
                post("/models/{id}/download")                { modelHandler.download(call) }
                delete("/models/{id}")                       { modelHandler.delete(call) }
                get("/models/{id}/download-progress")        { modelHandler.downloadProgress(call) }  // SSE

                // Sessions
                post("/sessions")                            { sessionHandler.create(call) }
                get("/sessions/{id}")                        { sessionHandler.get(call) }
                delete("/sessions/{id}")                     { sessionHandler.delete(call) }
                post("/sessions/{id}/reset")                 { sessionHandler.reset(call) }

                // Generation
                post("/sessions/{id}/generate")              { generateHandler.submit(call) }
                post("/sessions/{id}/transcribe")            { transcribeHandler.submit(call) }

                // Jobs
                get("/jobs/{id}")                            { jobHandler.get(call) }
                get("/jobs/{id}/stream")                     { jobHandler.stream(call) }  // SSE
                post("/jobs/{id}/cancel")                    { jobHandler.cancel(call) }

                // Diagnostics
                get("/diagnostics")                          { diagnosticsHandler.handle(call) }
            }
        }
    }
}
```

---

## Auth Middleware

Ktor's `Authentication` plugin with a custom bearer provider:

```kotlin
install(Authentication) {
    bearer("bearer") {
        authenticate { credential ->
            val token = credential.token
            if (tokenRepository.isValid(token)) {
                UserIdPrincipal(tokenRepository.getClientId(token))
            } else {
                null
            }
        }
    }
}
```

On failure Ktor returns `401 Unauthorized` automatically.

---

## SSE Streaming

Ktor's SSE plugin makes streaming straightforward:

```kotlin
// In JobHandler.stream():
call.respondSse {
    val jobFlow = jobRepository.streamJob(jobId)
    jobFlow.collect { chunk ->
        when (chunk) {
            is InferenceChunk.Token -> send(ServerSentEvent(
                data = Json.encodeToString(TokenEvent(chunk.text, chunk.index)),
                event = "token",
            ))
            is InferenceChunk.Done -> {
                send(ServerSentEvent(data = Json.encodeToString(DoneEvent(chunk.finishReason, chunk.usage)), event = "done"))
                return@collect
            }
            is InferenceChunk.Error -> {
                send(ServerSentEvent(data = Json.encodeToString(ErrorEvent(chunk.cause.message)), event = "error"))
                return@collect
            }
        }
    }
}

// Keep-alive ping every 15 s
launch {
    while (true) {
        delay(15_000)
        send(ServerSentEvent(comment = "ping"))
    }
}
```

---

## Port Discovery for Clients

Two mechanisms so client apps can find the server port without hardcoding:

### 1. Android ContentProvider
```kotlin
// In :app
class PortProvider : ContentProvider() {
    override fun query(...): Cursor {
        val port = settingsRepository.getServerPort()
        val cursor = MatrixCursor(arrayOf("port"))
        cursor.addRow(arrayOf(port))
        return cursor
    }
}
// Authority: app.altio.service.port
// Client: contentResolver.query(Uri.parse("content://app.altio.service.port"), ...)
```

### 2. Broadcast Intent
```kotlin
// Sent on server start and port change
val intent = Intent("app.altio.service.PORT_CHANGED").apply {
    putExtra("port", port)
}
context.sendBroadcast(intent)
```

Clients register a `BroadcastReceiver` for `app.altio.service.PORT_CHANGED` to handle port changes dynamically.

---

## `AiBackgroundService` (foreground service)

Keeps the process alive while the server is running.

```kotlin
class AiBackgroundService : Service() {
    @Inject lateinit var server: AiHttpServer

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        server.start()
        return START_STICKY
    }

    override fun onDestroy() {
        server.stop()
        super.onDestroy()
    }
}
```

---

## Unit & Integration Tests (`:server`)

All tests run on JVM using `ktor-server-test-host` — no emulator.

```kotlin
@Test
fun `health endpoint returns 200`() = testApplication {
    application { installPlugins(); testRoutes() }
    val response = client.get("/v1/health")
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<HealthResponse>()
    assertEquals("ok", body.status)
}

@Test
fun `generate endpoint returns 401 without token`() = testApplication {
    val response = client.post("/v1/sessions/ses_1/generate") {
        contentType(ContentType.Application.Json)
        setBody(GenerateRequest(messages = listOf(...)))
    }
    assertEquals(HttpStatusCode.Unauthorized, response.status)
}

@Test
fun `server binds only to loopback`() {
    val server = AiHttpServer(...)
    server.start()
    val port = server.port.value!!
    // Attempt connection from non-loopback address should fail
    assertThrows<ConnectException> {
        Socket("0.0.0.0", port).use { }
    }
    server.stop()
}
```
