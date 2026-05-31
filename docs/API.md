# Altio Service — API Reference

All endpoints are prefixed `/v1/`. The server binds to `127.0.0.1` on a random port chosen at
startup. Discover the port from the `PORT_CHANGED` broadcast or the `PortContentProvider`
(see [Client Integration Guide](CLIENT_INTEGRATION.md)).

Every request except `GET /v1/health` requires:
```
Authorization: Bearer <token>
```

---

## Error Format

All error responses share a common envelope:

```json
{
  "error": {
    "code": "SESSION_NOT_FOUND",
    "message": "Session abc123 does not exist"
  }
}
```

### Error Codes

| Code | HTTP Status | Meaning |
|------|------------|---------|
| `UNAUTHORIZED` | 401 | Missing or invalid bearer token |
| `SESSION_NOT_FOUND` | 404 | Session ID does not exist or belongs to another client |
| `JOB_NOT_FOUND` | 404 | Job ID does not exist or belongs to another client |
| `MODEL_NOT_LOADED` | 503 | No model is currently loaded |
| `MODEL_NOT_READY` | 409 | Model exists but is not yet ready (still downloading/verifying) |
| `MODEL_IN_USE` | 409 | Model/runtime is already in use |
| `REQUEST_TOO_LARGE` | 413 | Request body exceeds the per-route limit |
| `INVALID_INPUT` | 400 | Validation failed |
| `INFERENCE_FAILED` | — | Runtime error emitted as an SSE `error` event |
| `RATE_LIMITED` | 429 | Per-client concurrency limit reached |
| `CANCELLED` | — | Job was cancelled; emitted as an SSE `error` event |
| `UNSUPPORTED_FORMAT` | 415 | Audio format not accepted (MP3 only) |

---

## Health

### `GET /v1/health`
No authentication required. Used for connectivity checks.

**Response 200**
```json
{
  "status": "ok",
  "version": "1.0.0",
  "model_loaded": true,
  "active_sessions": 3,
  "active_jobs": 1,
  "uptime_ms": 120000
}
```

---

## Diagnostics

### `GET /v1/diagnostics`
Returns per-client runtime diagnostics. Requires auth.

**Response 200**
```json
{
  "uptime_seconds": 3600,
  "active_sessions": 2,
  "active_jobs": 1
}
```

---

## Models

### `GET /v1/models`
Lists all known models and their availability status.

**Response 200**
```json
{
  "models": [
    {
      "id": "gemma-4-e2b-it",
      "name": "Gemma 4 E2B IT",
      "status": "ready",
      "size_bytes": 2100000000,
      "capabilities": ["text", "vision", "audio"]
    }
  ]
}
```

`status` values: `not_downloaded` | `downloading` | `verifying` | `ready` | `loading` | `loaded`

### `POST /v1/models/{model_id}/download`
Starts or resumes a model download. Idempotent.

**Response 202**
```json
{ "model_id": "gemma-4-e2b-it", "status": "downloading" }
```

### `DELETE /v1/models/{model_id}`
Deletes downloaded model files from disk.

Returns **409** `MODEL_IN_USE` if the model is currently loaded into the runtime engine.
Close all sessions that use the model first, then retry.

**Response 204** — no body

### `GET /v1/models/{model_id}/download-progress`
SSE stream of download progress events.

**Response 200** `Content-Type: text/event-stream`
```
event: progress
data: {"received_bytes":52428800,"total_bytes":2100000000,"bytes_per_second":5242880,"eta_ms":392000}

event: complete
data: {"model_id":"gemma-4-e2b-it"}

event: error
data: {"code":"DOWNLOAD_FAILED","message":"Network error"}
```

---

## Sessions

A session maintains conversation history and is scoped to a single model. LiteRT currently
supports **one active runtime session at a time**. The per-client API cap remains **20 sessions**
for future multi-session runtimes, but creating another session while one is active currently
returns **409** `MODEL_IN_USE`.

The service dashboard's **Test Model** action uses the same LiteRT runtime. It will ask you to close
active sessions before testing the model instead of opening a second runtime session.

### `POST /v1/sessions`
Creates a new session and loads the model into the runtime engine (if not already loaded).

**Request**
```json
{
  "model_id": "gemma-4-e2b-it",
  "system_prompt": "You are a helpful assistant.",
  "temperature": 0.8,
  "top_k": 64,
  "top_p": 0.95,
  "max_tokens": 2048
}
```

`system_prompt`, `temperature`, `top_k`, `top_p`, and `max_tokens` are optional.

**Response 201**
```json
{
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "model_id": "gemma-4-e2b-it",
  "client_id": "client_abc",
  "created_at": 1744790400000
}
```

Returns **409** `MODEL_IN_USE` if another runtime session is already active.
Returns **503** if the model fails to load into the runtime engine.

### `GET /v1/sessions/{session_id}`

**Response 200**
```json
{
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "model_id": "gemma-4-e2b-it",
  "client_id": "client_abc",
  "system_prompt": null,
  "message_count": 4,
  "created_at": 1744790400000,
  "last_active_at": 1744790700000
}
```

Returns **404** `SESSION_NOT_FOUND` if the session does not exist or belongs to another client.

### `DELETE /v1/sessions/{session_id}`
Closes the session and frees its runtime state. Cancels any running job.

**Response 204**

### `POST /v1/sessions/{session_id}/reset`
Clears conversation history while keeping the session open.

**Response 204**

---

## Text Generation

Maximum **5 concurrent active jobs** per client token.

### `POST /v1/sessions/{session_id}/generate`
Submits a generation request. Returns a job ID immediately (async).

**Request** — `Content-Type: application/json`, max **1 MB**
```json
{
  "messages": [
    { "role": "user", "content": "Explain quantum entanglement simply." }
  ]
}
```

For vision input, `content` may be an array:
```json
{
  "messages": [
    {
      "role": "user",
      "content": [
        { "type": "text", "text": "What is in this image?" },
        { "type": "image_url", "image_url": { "url": "data:image/png;base64,..." } }
      ]
    }
  ]
}
```

**Response 202**
```json
{
  "job_id": "job_xyz789",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "queued"
}
```

Returns **429** `RATE_LIMITED` when the active job cap is reached.

---

## Jobs

### `GET /v1/jobs/{job_id}`
Polls job status and result.

**Response 200**
```json
{
  "job_id": "job_xyz789",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "completed",
  "output": "Quantum entanglement is when two particles…",
  "created_at": 1744790400000,
  "started_at": 1744790401000,
  "completed_at": 1744790404000
}
```

`status` values: `queued` | `running` | `completed` | `failed` | `cancelled`

### `GET /v1/jobs/{job_id}/stream`
SSE stream of tokens. Safe to open before the job starts — the server replays all tokens emitted
so far when a new subscriber connects.

**Response 200** `Content-Type: text/event-stream`
```
event: token
data: {"text":"Quantum","index":0}

event: token
data: {"text":" entanglement","index":1}

event: done
data: {"finish_reason":"stop","prompt_tokens":12,"completion_tokens":87}

event: error
data: {"message":"INFERENCE_FAILED"}
```

Keep-alive comments are sent every 15 seconds:
```
: ping
```

### `POST /v1/jobs/{job_id}/cancel`
Cancels a queued or running job.

**Response 204**

Returns **404** `JOB_NOT_FOUND` if the job does not exist or belongs to another client.
Returns **409** if the job is already in a terminal state.

---

## Audio Transcription

### `POST /v1/sessions/{session_id}/transcribe`
Uploads an MP3 and creates a transcription job.

**Request** — `Content-Type: multipart/form-data`, max **25 MB**
```
--boundary
Content-Disposition: form-data; name="audio"; filename="recording.mp3"
Content-Type: audio/mpeg

<binary mp3 data>
--boundary--
```

Constraints: MP3 format only, ≤ 25 MB.

**Response 202**
```json
{
  "job_id": "job_tr456",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "queued"
}
```

Poll `GET /v1/jobs/{job_id}` until `status` is `completed`. The `output` field contains:
```json
{
  "transcript": "Hello, this is a test recording.",
  "language": "en",
  "duration_ms": 3200
}
```

---

## Request Limits

| Limit | Value |
|-------|-------|
| Max JSON body (generate) | 1 MB |
| Max multipart body (transcribe) | 25 MB |
| Max concurrent jobs per token | 5 |
| Max active sessions per token | 20 |
| SSE keep-alive interval | 15 seconds |

---

## Versioning

- All endpoints are prefixed `/v1/`.
- Breaking changes ship as `/v2/` with a deprecation notice.
- Additive changes (new optional fields, new endpoints) are non-breaking.
- Clients should ignore unknown JSON fields (`ignoreUnknownKeys = true`).
