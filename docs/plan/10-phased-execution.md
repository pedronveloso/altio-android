# 10 — Phased Execution

Each phase has a clear goal, concrete deliverables, and a definition of done. Phases should be executed in order; later phases can be broken into sub-tasks as needed.

---

## Phase 1 — Foundation

**Goal:** Multi-module Gradle project compiles; `/v1/health` endpoint works; CI lint passes.

### Tasks
- [ ] Convert `:app` into a multi-module project with `:core:domain`, `:core:data`, `:server`, `:runtime:litert`, `:ui`, `:demo`
- [ ] Add `libs.versions.toml` with all planned dependencies (see `01-module-architecture.md`)
- [ ] Update `minSdk` to 31 across all modules
- [ ] Configure Hilt in `:app` (`@HiltAndroidApp`, AppModule)
- [ ] Implement `AiHttpServer` skeleton with CIO engine bound to `127.0.0.1`
- [ ] Implement `GET /v1/health` returning hardcoded `{"status":"ok"}`
- [ ] Write unit test: health endpoint returns 200
- [ ] Write unit test: server binds only to loopback
- [ ] Add ktlint + Detekt to root build; CI runs lint and tests

**Done when:** `./gradlew test` passes; health endpoint responds over `curl http://127.0.0.1:<port>/v1/health` on a real device.

---

## Phase 2 — Model Management

**Goal:** User can download Gemma 3n E2B IT INT4 via the UI; file is verified and stored correctly.

### Tasks
- [ ] Define `models.json` asset with Gemma 3n E2B IT INT4 entry (download URL, sha256, size)
- [ ] Implement `ModelEntity` Room table + DAO
- [ ] Implement `ModelDownloadWorker` (CoroutineWorker, resumable HTTP, foreground service, progress reporting)
- [ ] Implement `DownloadRepository`
- [ ] Implement SHA-256 verification post-download
- [ ] Wire `GET /v1/models` and `POST /v1/models/{id}/download` HTTP endpoints
- [ ] Implement `GET /v1/models/{id}/download-progress` SSE endpoint
- [ ] Add `ModelDownloadScreen` Compose UI with progress bar and ETA
- [ ] Add `ModelManagerScreen` listing model status
- [ ] Write unit tests: worker progress reporting, file rename on success, checksum mismatch deletes temp file
- [ ] Write Compose UI test: progress bar updates on state change

**Done when:** Model downloads end-to-end on device; verified file present at expected path; UI shows real-time progress.

---

## Phase 3 — Runtime Integration (Text Only)

**Goal:** Blocking (non-streaming) text generation works end-to-end.

### Tasks
- [ ] Implement `RuntimeProvider`, `RuntimeEngine`, `RuntimeSession` interfaces in `:core:domain`
- [ ] Implement `LiteRtRuntimeProvider` and `LiteRtRuntimeEngine` in `:runtime:litert`
- [ ] Implement `LiteRtRuntimeSession.generateStream()` (use `callbackFlow` wrapping `sendMessageAsync`)
- [ ] Implement `RuntimeEngineHolder` singleton
- [ ] Implement `POST /v1/sessions` (create session) and `DELETE /v1/sessions/{id}`
- [ ] Implement `POST /v1/sessions/{id}/generate` (async job, returns job ID)
- [ ] Implement `GET /v1/jobs/{id}` polling endpoint (accumulates all tokens into final result)
- [ ] Implement `InferenceScheduler` with semaphore(1)
- [ ] Store jobs in Room `jobs` table
- [ ] Write instrumented test: `LiteRtRuntimeSession` emits tokens for a short prompt

**Done when:** Demo app can create a session, submit a prompt, poll for the result, and display the completed text.

---

## Phase 4 — Streaming

**Goal:** Clients receive tokens in real time via SSE.

### Tasks
- [ ] Install Ktor SSE plugin in `:server`
- [ ] Implement `GET /v1/jobs/{id}/stream` SSE endpoint
- [ ] Implement replay buffer in `InferenceScheduler` (`shareIn` with `replay = MAX_VALUE`)
- [ ] Implement keep-alive ping (SSE comment every 15 s)
- [ ] Add `streamJob()` in demo app's `AiServiceClient`
- [ ] Add `ChatScreen` in demo app that renders tokens as they arrive
- [ ] Write integration test: SSE stream emits `token` events then `done` event

**Done when:** Demo app chat screen shows tokens appearing word-by-word in real time.

---

## Phase 5 — Session Lifecycle & Cancellation

**Goal:** Sessions isolate history; jobs can be cancelled mid-inference.

### Tasks
- [ ] Implement `SessionRepository` with Room backing
- [ ] Implement `GET /v1/sessions/{id}`, `POST /v1/sessions/{id}/reset`
- [ ] Implement `POST /v1/jobs/{id}/cancel`
- [ ] Wire cancellation to `conversation.stopGeneration()` in LiteRT session
- [ ] Implement idle session cleanup (30-min inactivity → auto-close)
- [ ] Write unit test: two sessions from same engine have independent message counts after `reset()`
- [ ] Write unit test: cancel job → subsequent poll returns `CANCELLED` status

**Done when:** Demo app can open two independent chat sessions; cancelling one job does not affect the other.

---

## Phase 6 — Audio Transcription

**Goal:** Client uploads MP3; receives transcript via job polling.

### Tasks
- [ ] Implement `POST /v1/sessions/{id}/transcribe` (multipart upload)
- [ ] Implement audio validation (format, size, duration limits)
- [ ] Implement `LiteRtRuntimeSession.transcribe()` using LiteRT-LM audio backend
- [ ] Add audio job type to `JobEntity`/`JobResult`
- [ ] Add `AudioScreen` in demo app: file picker → upload → show transcript
- [ ] Write unit test: audio validation rejects oversized files and wrong MIME type
- [ ] Write instrumented test: transcription of a 3-second test audio clip returns non-empty transcript

**Done when:** Demo app can upload a short MP3 and display the transcript.

---

## Phase 7 — Security

**Goal:** All endpoints protected; sessions scoped to caller identity.

### Tasks
- [ ] Implement `ClientTokenEntity` Room table + DAO
- [ ] Implement `TokenRepository` (generate, validate, revoke)
- [ ] Implement Ktor bearer auth middleware
- [ ] Add `clientId` to all Session and Job entities
- [ ] Enforce ownership checks in all session/job handlers
- [ ] Implement per-client concurrency limits (max 5 jobs, max 20 sessions)
- [ ] Implement request size limits (global 30 MB; per-route 1 MB for JSON)
- [ ] Add `TokenManagerScreen` in `:ui`
- [ ] Write unit tests: invalid token → 401; wrong client accessing session → SESSION_NOT_FOUND; rate limit triggers 429

**Done when:** Demo app requires a valid token; one token's sessions are not visible to another token.

---

## Phase 8 — Service Lifecycle & UI Polish

**Goal:** Service auto-starts, survives in background, shuts down when idle; full UI setup flow.

### Tasks
- [ ] Implement `AiBackgroundService` (foreground service, persistent notification)
- [ ] Add "Start on Boot" `BroadcastReceiver` (optional, off by default)
- [ ] Implement idle shutdown timer (configurable via Settings)
- [ ] Implement ContentProvider + broadcast for port discovery
- [ ] Implement full setup flow: `WelcomeScreen` → `PermissionsScreen` → `ModelDownloadScreen`
- [ ] Implement `DashboardScreen` with live server status, session count, job count
- [ ] Implement `SettingsScreen` (idle timer, accelerator, max tokens)
- [ ] Apply Material 3 dynamic color with teal seed fallback
- [ ] Write Compose UI tests for setup flow happy path

**Done when:** First-time user can complete setup without needing to read docs; service persists across app backgrounding.

---

## Phase 9 — Demo App Completion

**Goal:** Demo app covers all API endpoints; usable as reference integration.

### Tasks
- [ ] Complete `AiServiceClient` Retrofit wrapper covering all endpoints
- [ ] Implement `JobsScreen` (list + cancel)
- [ ] Implement `LogScreen` (raw HTTP log)
- [ ] Implement `HealthScreen` (diagnostics display)
- [ ] Add `GET /v1/diagnostics` endpoint and `DiagnosticsHandler`
- [ ] Document the demo app's usage in `demo/README.md`

**Done when:** Demo app exercises every documented endpoint; shows raw request/response for debugging.

---

## Phase 10 — Hardening & Documentation

**Goal:** Production-ready: edge cases handled, memory pressure handled, docs complete.

### Tasks
- [ ] Memory pressure handling: register `ComponentCallbacks2`, unload model on `TRIM_MEMORY_CRITICAL`
- [ ] Handle `Engine.load()` failure gracefully (return 503 Service Unavailable)
- [ ] Handle all `RuntimeSession.generateStream()` error paths
- [ ] Implement `DELETE /v1/models/{id}` with guard for loaded model
- [ ] Add Detekt + ktlint CI gates
- [ ] Write `docs/API.md` (human-readable API reference mirroring `02-api-design.md`)
- [ ] Write `docs/CLIENT_INTEGRATION.md` (quick-start for third-party Android devs)
- [ ] Review all error codes for consistency with `02-api-design.md`
- [ ] Final Compose UI accessibility pass (content descriptions, touch targets)
- [ ] Run LiteRT-LM on CPU + GPU and confirm both accelerators work

**Done when:** App survives a 1-hour soak test with 3 concurrent clients; no crashes; memory stable.

---

## Milestone Summary

| Milestone | After Phase | Key Capability |
|-----------|------------|----------------|
| M1 — Server skeleton | 1 | HTTP server boots; health endpoint works |
| M2 — Model ready | 2 | Model downloaded, verified, stored |
| M3 — Inference | 3 | Text generation over HTTP (polling) |
| M4 — Streaming | 4 | Real-time token streaming via SSE |
| M5 — Full feature | 6 | Audio transcription; session management |
| M6 — Secure | 7 | Auth tokens; session isolation |
| M7 — Shippable | 10 | Full UI; stable; documented |
