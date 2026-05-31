# Altio Demo

The `:demo` module is a self-contained reference client for the Altio Service loopback API.
It exercises every documented endpoint and doubles as the manual end-to-end test harness.

---

## Prerequisites

1. **Install the main app** (`app` module) on the same device.
2. **Download a model** by completing the setup flow (Welcome → Permissions → Model Download).
3. **Generate a bearer token** in Settings → Manage API Tokens. Copy the raw token — it is
   shown exactly once.
4. **Note the port** displayed on the Dashboard screen (e.g. `54231`).

---

## First Launch

On first launch the demo shows a **Config screen**. Enter:

| Field | Value |
|-------|-------|
| Port | The loopback port from the Dashboard (e.g. `54231`) |
| Bearer Token | The raw token copied from Settings |

Tap **Connect**. The demo creates a session automatically and proceeds to the main tabs.
Config and the session ID are saved to SharedPreferences so you don't have to re-enter them.

To change config (e.g. after revoking a token) force-stop the demo app and re-launch, or
implement the "Change config" path shown when a session creation error occurs.

---

## Tabs

### Chat
Real-time text generation via SSE.

- Type a message and tap **Send**.
- Tokens appear word-by-word as they stream from the model.
- Each submitted generate job is tracked in the **Jobs** tab.

### Audio
Audio transcription via job polling.

- Tap **Pick audio file** and choose an audio file, or use **Start recording** / **Stop recording**
  to capture AAC audio in-app.
- Tap **Transcribe** — the selected file is uploaded and a transcription job is created.
- The screen polls the job every 1.5 s and displays the transcript on completion.
- The job appears in the **Jobs** tab.

### Jobs
Live view of all jobs submitted during this session.

- Statuses poll automatically every 2 seconds.
- Tap **Cancel** on a `QUEUED` or `RUNNING` job to cancel it.
- Tap **Clear done** to remove completed/cancelled/failed jobs from the list.

### Health
Server health check and per-client diagnostics.

- Calls `GET /v1/health` (no auth required) and `GET /v1/diagnostics` (auth required).
- Shows server status, uptime, active session count, and active job count.
- Tap **Refresh** to re-fetch.

### Log
Raw HTTP request and response log.

- Every network call made by the demo is captured here via OkHttp's
  `HttpLoggingInterceptor` (BODY level).
- Requests are highlighted in primary color; responses in secondary color.
- Tap **Clear** to reset the buffer.

---

## API Coverage

| Endpoint | Tab |
|----------|-----|
| `GET /v1/health` | Health |
| `GET /v1/diagnostics` | Health |
| `POST /v1/sessions` | (auto on connect) |
| `POST /v1/sessions/{id}/generate` | Chat |
| `GET /v1/jobs/{id}/stream` | Chat |
| `POST /v1/sessions/{id}/transcribe` | Audio |
| `GET /v1/jobs/{id}` | Audio, Jobs |
| `POST /v1/jobs/{id}/cancel` | Jobs |

---

## Extracting the Client Library

`AiServiceClient.kt` is designed to be self-contained. To use it in another project:

1. Copy `AiServiceClient.kt` into your project.
2. Add `com.squareup.okhttp3:okhttp` and `org.jetbrains.kotlinx:kotlinx-serialization-json`
   as dependencies.
3. Construct with your port + bearer token:

```kotlin
val client = AiServiceClient(port = 54231, bearerToken = "your-token")

// Create a session
val sessionId = client.createSession("gemma-4-e2b-it")

// Stream a generation
client.streamJob(client.generate(sessionId, "Hello!")).collect { event ->
    if (event.event == "token") println(event.data)
}
```
