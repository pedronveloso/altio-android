# 02 — API Design

All endpoints live under `/v1/`. Requests and responses use `application/json` unless otherwise noted.
Authentication uses `Authorization: Bearer <token>` on every request except `/v1/health`.

---

## Base URL

```
http://127.0.0.1:<port>/v1
```

The port is dynamic (first free port on startup). Clients discover it by reading the broadcast
intent `app.altio.service.PORT_CHANGED` or by querying the ContentProvider
`content://app.altio.service.port` — both emit the current port as an integer string.

---

## Common Structures

### Error Response
```json
{
  "error": {
    "code": "SESSION_NOT_FOUND",
    "message": "Session abc123 does not exist",
    "details": {}
  }
}
```

### Error Codes (string enum)
```
UNAUTHORIZED           Missing or invalid bearer token
SESSION_NOT_FOUND      Session ID does not exist
JOB_NOT_FOUND          Job ID does not exist
MODEL_NOT_LOADED       No model is currently loaded
MODEL_NOT_READY        Model is still loading
REQUEST_TOO_LARGE      Request body exceeds limit
INVALID_INPUT          Validation failed (see details)
INFERENCE_FAILED       Runtime error during generation
RATE_LIMITED           Too many concurrent requests
SERVER_BUSY            Scheduler at capacity
CANCELLED              Job was cancelled by client
UNSUPPORTED_FORMAT     Audio format not accepted
```

---

## Endpoints

### Health

#### `GET /v1/health`
No auth required.

**Response 200**
```json
{
  "status": "ok",
  "version": "1.0.0",
  "model_loaded": true,
  "model_id": "gemma-3n-e2b-it-int4",
  "active_sessions": 3,
  "active_jobs": 1,
  "uptime_ms": 120000
}
```

---

### Models

#### `GET /v1/models`
Lists all known models and their availability status.

**Response 200**
```json
{
  "models": [
    {
      "id": "gemma-3n-e2b-it-int4",
      "name": "Gemma 3n E2B IT INT4",
      "size_bytes": 2100000000,
      "status": "ready",
      "capabilities": ["text", "vision", "audio"],
      "download_progress": null
    }
  ]
}
```

`status` values: `not_downloaded` | `downloading` | `verifying` | `ready` | `loading` | `loaded`

#### `POST /v1/models/{model_id}/download`
Starts or resumes a model download.

**Response 202**
```json
{ "model_id": "gemma-3n-e2b-it-int4", "status": "downloading" }
```

#### `DELETE /v1/models/{model_id}`
Deletes the downloaded model files. Fails if model is currently loaded.

**Response 204** (no body)

#### `GET /v1/models/{model_id}/download-progress`
SSE stream of download progress events.

**Response 200** `Content-Type: text/event-stream`
```
event: progress
data: {"received_bytes":52428800,"total_bytes":2100000000,"bytes_per_second":5242880,"eta_ms":392000}

event: complete
data: {"model_id":"gemma-3n-e2b-it-int4"}

event: error
data: {"code":"DOWNLOAD_FAILED","message":"Network error"}
```

---

### Sessions

#### `POST /v1/sessions`
Creates a new isolated session.

**Request**
```json
{
  "model_id": "gemma-3n-e2b-it-int4",
  "system_prompt": "You are a helpful assistant.",
  "config": {
    "temperature": 0.8,
    "top_k": 64,
    "top_p": 0.95,
    "max_tokens": 2048
  }
}
```

**Response 201**
```json
{
  "session_id": "ses_abc123",
  "model_id": "gemma-3n-e2b-it-int4",
  "created_at": "2026-04-13T10:00:00Z"
}
```

#### `GET /v1/sessions/{session_id}`
Returns session metadata.

**Response 200**
```json
{
  "session_id": "ses_abc123",
  "model_id": "gemma-3n-e2b-it-int4",
  "created_at": "2026-04-13T10:00:00Z",
  "message_count": 4,
  "last_active_at": "2026-04-13T10:05:00Z"
}
```

#### `DELETE /v1/sessions/{session_id}`
Closes and deletes the session. Cancels any running job.

**Response 204**

#### `POST /v1/sessions/{session_id}/reset`
Clears the conversation history while keeping the session open.

**Response 200**
```json
{ "session_id": "ses_abc123", "message_count": 0 }
```

---

### Text Generation (Chat)

#### `POST /v1/sessions/{session_id}/generate`
Submits a generation request. Returns a job ID immediately (async pattern).

**Request**
```json
{
  "messages": [
    { "role": "user", "content": "Explain quantum entanglement simply." }
  ],
  "stream": false
}
```

For vision input, `content` can be an array:
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
  "session_id": "ses_abc123",
  "status": "queued"
}
```

#### `GET /v1/jobs/{job_id}`
Polls job status and result.

**Response 200**
```json
{
  "job_id": "job_xyz789",
  "status": "completed",
  "result": {
    "content": "Quantum entanglement is when two particles...",
    "finish_reason": "stop",
    "usage": {
      "prompt_tokens": 12,
      "completion_tokens": 87,
      "total_tokens": 99
    }
  },
  "created_at": "2026-04-13T10:00:00Z",
  "completed_at": "2026-04-13T10:00:03Z"
}
```

`status` values: `queued` | `running` | `completed` | `failed` | `cancelled`

#### `GET /v1/jobs/{job_id}/stream`
SSE stream of tokens for a running or queued job. Safe to open before the job starts.

**Response 200** `Content-Type: text/event-stream`
```
event: token
data: {"token":"Quantum","index":0}

event: token
data: {"token":" entanglement","index":1}

event: done
data: {"finish_reason":"stop","usage":{"prompt_tokens":12,"completion_tokens":87,"total_tokens":99}}

event: error
data: {"code":"INFERENCE_FAILED","message":"Runtime exception"}
```

#### `POST /v1/jobs/{job_id}/cancel`
Requests cancellation of a running or queued job.

**Response 200**
```json
{ "job_id": "job_xyz789", "status": "cancelled" }
```

---

### Audio Transcription

#### `POST /v1/sessions/{session_id}/transcribe`
Uploads an audio file and returns a job ID. Transcription runs asynchronously.

**Request** `Content-Type: multipart/form-data`
```
--boundary
Content-Disposition: form-data; name="audio"; filename="recording.mp3"
Content-Type: audio/mpeg

<binary mp3 data>
--boundary--
```

Constraints:
- Format: MP3 only (v1)
- Max size: 25 MB
- Max duration: 5 minutes

**Response 202**
```json
{
  "job_id": "job_tr456",
  "session_id": "ses_abc123",
  "status": "queued"
}
```

The job follows the same polling pattern as text generation (`GET /v1/jobs/{job_id}`).

**Completed job result**
```json
{
  "job_id": "job_tr456",
  "status": "completed",
  "result": {
    "transcript": "Hello, this is a test recording.",
    "language": "en",
    "duration_ms": 3200
  }
}
```

---

### Server Diagnostics

#### `GET /v1/diagnostics`
Returns internal state for debugging (requires auth).

**Response 200**
```json
{
  "server_version": "1.0.0",
  "model_id": "gemma-3n-e2b-it-int4",
  "model_status": "loaded",
  "sessions": { "total": 5, "active": 2 },
  "jobs": { "queued": 1, "running": 1, "completed_last_hour": 42 },
  "memory": { "heap_used_mb": 412, "native_used_mb": 1900 },
  "accelerator": "GPU"
}
```

---

## Streaming Format Detail

All SSE streams follow the format:

```
event: <event-name>\n
data: <json-object>\n
\n
```

Clients must handle:
- `event: token` — partial text chunk
- `event: done` — final event, stream ends
- `event: error` — terminal error, stream ends
- `event: progress` — for download and long-running transcription

The stream connection is kept alive with SSE comment pings every 15 seconds:
```
: ping\n\n
```

---

## Versioning Policy

- All endpoints are prefixed `/v1/`
- Breaking changes will be introduced as `/v2/` with a deprecation notice in response headers
- Additive changes (new optional fields, new endpoints) are non-breaking and do not require a version bump
- Clients should ignore unknown JSON fields

---

## Request Limits

| Limit | Value |
|-------|-------|
| Max request body | 30 MB (audio upload) |
| Max prompt length | 128K tokens (model limit) |
| Max concurrent jobs per token | 5 |
| Max active sessions per token | 20 |
| SSE stream timeout (no activity) | 5 minutes |
