# Altio Service — Client Integration Guide

This guide shows how to call the Altio Service HTTP API from another Android app running on
the same device.

---

## 1. Discover the Port

The service binds to `127.0.0.1` on a persistent configured port. By default that port is
`52731`, and the device operator can change it in **Settings**. There are two ways to discover the
active port:

### Option A — ContentProvider (recommended, synchronous)

> [!IMPORTANT]
> Because the `PortContentProvider` is protected, client apps must declare the following normal permission in their `AndroidManifest.xml`, otherwise `ContentResolver.query(...)` will throw a `SecurityException`:
> ```xml
> <uses-permission android:name="app.altio.service.permission.READ_PORT" />
> ```

```kotlin
fun resolvePort(context: Context): Int? {
    val uri = Uri.parse("content://app.altio.service.port/port")
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(cursor.getColumnIndexOrThrow("port"))
            .takeIf { it > 0 }
        else null
    }
}
```

Returns `null` if the service is not running, or the active port as an `Int` otherwise.

### Option B — Broadcast Receiver

Register for `app.altio.service.PORT_CHANGED` to receive port updates reactively when the service
restarts or the configured port changes:

```kotlin
val receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val port = intent.getIntExtra("port", -1)
        if (port > 0) onPortChanged(port)
    }
}

ContextCompat.registerReceiver(
    context,
    receiver,
    IntentFilter("app.altio.service.PORT_CHANGED"),
    ContextCompat.RECEIVER_NOT_EXPORTED,
)
```

Unregister when no longer needed to avoid leaks.

---

## 2. Obtain a Bearer Token

Tokens are managed by the device operator through the main app:

1. Open the **Altio Service** app.
2. Go to **Settings → Manage API Tokens**.
3. Tap **Generate New Token**, enter a label, and copy the displayed token.

The raw token is shown exactly once. Store it securely (e.g. Android `EncryptedSharedPreferences`
or the system `KeyStore`).

---

## 3. Make Your First Request

All API calls use `http://127.0.0.1:<port>/v1` as the base URL. Every request (except health)
must include:
```
Authorization: Bearer <your-token>
Content-Type: application/json
```

### Minimal Kotlin client (OkHttp)

```kotlin
class AiServiceClient(port: Int, token: String) {
    private val base = "http://127.0.0.1:$port/v1"
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val mt = "application/json".toMediaType()

    private fun Request.Builder.auth() = addHeader("Authorization", "Bearer $token")

    // Check connectivity — no auth required
    suspend fun health(): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url("$base/health").get().build()).execute()
            .use { it.body.string() }
    }

    // Create a session
    suspend fun createSession(modelId: String): String = withContext(Dispatchers.IO) {
        val body = """{"model_id":"$modelId"}""".toRequestBody(mt)
        http.newCall(Request.Builder().url("$base/sessions").post(body).auth().build()).execute()
            .use { r ->
                check(r.isSuccessful) { "createSession failed: ${r.code}" }
                json.parseToJsonElement(r.body.string())
                    .jsonObject["session_id"]!!.jsonPrimitive.content
            }
    }

    // Submit generation, get job ID back
    suspend fun generate(sessionId: String, prompt: String): String = withContext(Dispatchers.IO) {
        val body = """{"messages":[{"role":"user","content":"$prompt"}]}""".toRequestBody(mt)
        http.newCall(
            Request.Builder().url("$base/sessions/$sessionId/generate").post(body).auth().build()
        ).execute().use { r ->
            check(r.isSuccessful) { "generate failed: ${r.code}" }
            json.parseToJsonElement(r.body.string())
                .jsonObject["job_id"]!!.jsonPrimitive.content
        }
    }

    // Poll job until terminal, return output text
    suspend fun awaitResult(jobId: String): String? = withContext(Dispatchers.IO) {
        while (true) {
            delay(500)
            val resp = http.newCall(
                Request.Builder().url("$base/jobs/$jobId").get().auth().build()
            ).execute().use { it.body.string() }
            val obj = json.parseToJsonElement(resp).jsonObject
            when (obj["status"]?.jsonPrimitive?.content) {
                "completed" -> return@withContext obj["output"]?.jsonPrimitive?.content
                "failed", "cancelled" -> return@withContext null
            }
        }
        @Suppress("UNREACHABLE_CODE") null
    }
}
```

---

## 4. Stream Tokens in Real Time (SSE)

For a real-time chat experience, open the `stream` endpoint and parse Server-Sent Events:

```kotlin
fun streamTokens(jobId: String): Flow<String> = flow {
    val request = Request.Builder()
        .url("$base/jobs/$jobId/stream")
        .addHeader("Authorization", "Bearer $token")
        .addHeader("Accept", "text/event-stream")
        .build()

    http.newCall(request).execute().use { response ->
        val source = response.body.source()
        var eventName = ""
        var data = ""

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            when {
                line.startsWith(":") -> {} // keep-alive ping, ignore
                line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                line.startsWith("data:") -> data = line.removePrefix("data:").trim()
                line.isEmpty() && data.isNotEmpty() -> {
                    if (eventName == "token") {
                        val text = Json.parseToJsonElement(data)
                            .jsonObject["text"]?.jsonPrimitive?.content ?: ""
                        emit(text)
                    }
                    if (eventName == "done" || eventName == "error") return@flow
                    eventName = ""; data = ""
                }
            }
        }
    }
}
```

Collect in a `ViewModel`:

```kotlin
viewModelScope.launch {
    val sessionId = client.createSession("gemma-4-e2b-it")
    val jobId     = client.generate(sessionId, userInput)
    client.streamTokens(jobId).collect { token ->
        _uiState.update { it.copy(response = it.response + token) }
    }
}
```

---

## 5. Audio Transcription

Upload an MP3 file and poll for the transcript:

```kotlin
suspend fun transcribe(sessionId: String, audioBytes: ByteArray): String? {
    val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
        .addFormDataPart("audio", "recording.mp3",
            audioBytes.toRequestBody("audio/mpeg".toMediaType()))
        .build()

    val jobId = http.newCall(
        Request.Builder().url("$base/sessions/$sessionId/transcribe")
            .post(multipart).auth().build()
    ).execute().use { r ->
        check(r.isSuccessful) { "transcribe failed: ${r.code}" }
        json.parseToJsonElement(r.body.string())
            .jsonObject["job_id"]!!.jsonPrimitive.content
    }

    val output = awaitResult(jobId) ?: return null
    return json.parseToJsonElement(output).jsonObject["transcript"]?.jsonPrimitive?.content
}
```

---

## 6. Gradle Dependencies

```kotlin
// In your module's build.gradle.kts
dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
```

---

## 7. Tips

| Tip | Detail |
|-----|--------|
| **Re-use the session** | One session per conversation thread. Only create a new one when starting a fresh topic. |
| **One active LiteRT session** | LiteRT currently supports one active runtime session at a time. If `POST /v1/sessions` returns 409 `MODEL_IN_USE`, close the active session before creating another or before using the dashboard Test Model action. |
| **Retry on 503** | If `POST /v1/sessions` returns 503, the model is still loading — wait 1–2 s and retry. |
| **Handle port changes** | Register the broadcast receiver before querying the ContentProvider; the port can change if the service restarts onto a different configured value. |
| **Token security** | Store tokens in `EncryptedSharedPreferences` or Android `KeyStore`. Never log or transmit them over a network connection. |
| **Cancel on navigation** | Cancel the OkHttp call or the coroutine job when the user leaves the screen to avoid orphaned jobs consuming GPU time. |
