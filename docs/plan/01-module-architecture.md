# 01 — Module Architecture

## Module Map

```
altio-ai-service/
├── app/                    # :app — application entry point, Hilt root
├── core/
│   ├── domain/             # :core:domain — pure Kotlin, no Android deps
│   └── data/               # :core:data — Room, DataStore, download infra
├── server/                 # :server — Ktor HTTP server, routing, middleware
├── runtime/
│   └── litert/             # :runtime:litert — LiteRT-LM implementation
├── ui/                     # :ui — Compose screens, ViewModels
└── demo/                   # :demo — standalone client app for testing
```

---

## Module Responsibilities

### `:core:domain`
Pure Kotlin module. No Android framework dependencies. Contains:

- **Entities**: `Model`, `Session`, `Job`, `InferenceRequest`, `InferenceResult`, `TranscriptionResult`
- **Repository interfaces**: `ModelRepository`, `SessionRepository`, `JobRepository`
- **Runtime interfaces**: `RuntimeProvider`, `RuntimeSession`, `StreamingCallback`
- **Use cases**: `CreateSessionUseCase`, `SubmitJobUseCase`, `CancelJobUseCase`, `GetJobStatusUseCase`
- **Error types**: sealed `AltioError` hierarchy

Dependencies: none (pure Kotlin + Coroutines)

---

### `:core:data`
Android library. Implements the repository interfaces from `:core:domain`.

- **Room database**: `AltioDatabase` with DAOs for `SessionEntity`, `JobEntity`, `ModelEntity`
- **DataStore**: `SettingsRepository` (server port, auth tokens, idle timeout)
- **Download infrastructure**: `ModelDownloadRepository` using WorkManager + CoroutineWorker
  - Resumable HTTP downloads with `Range` headers
  - Progress tracked via `WorkInfo` updates
  - File verification (SHA-256 checksum)
  - Storage path: `context.getExternalFilesDir("models")/{modelId}/{version}/`
- **Model registry**: reads bundled `models.json` asset listing available models

Dependencies: `:core:domain`, Room, DataStore, WorkManager, OkHttp (for downloads)

---

### `:server`
Android library. Runs the embedded Ktor HTTP server.

- **`AiHttpServer`**: lifecycle-aware server wrapper (start/stop)
- **Routing**: all `/v1/...` routes wired to use-case calls
- **Middleware**: auth token validation, request size limits, JSON content negotiation
- **Streaming**: Server-Sent Events (SSE) support for token streaming
- **Port management**: finds a free loopback port on startup; persists it in DataStore so clients can discover it

Dependencies: `:core:domain`, Ktor Server (CIO engine), Kotlinx Serialization

---

### `:runtime:litert`
Android library. Implements `RuntimeProvider` using the LiteRT-LM SDK.

- **`LiteRtRuntimeProvider`**: initializes `Engine` with `EngineConfig` (CPU/GPU/NPU accelerator)
- **`LiteRtSession`**: wraps `Conversation`; exposes coroutine-based `generateStream()` and `transcribe()`
- **Session isolation**: each `Session` entity gets its own `Conversation` instance
- **Model loading**: loads from path provided by `:core:data`
- **Resource management**: `close()` on engine and conversation

Dependencies: `:core:domain`, `litertlm-android`

---

### `:ui`
Android library. All Compose screens and their ViewModels.

- **`SetupScreen`**: first-run wizard (permissions, model download)
- **`ModelManagerScreen`**: list models, download/delete actions, download progress
- **`ServiceDashboardScreen`**: server status, active sessions, active jobs, server URL display
- **`SettingsScreen`**: port, idle timeout, token management
- **Navigation**: single Compose nav graph
- **ViewModels**: `@HiltViewModel`, observe repository Flows, delegate actions to use cases

Dependencies: `:core:domain`, `:core:data`, Compose, Material 3, Hilt

---

### `:app`
Thin application module. Wires everything together.

- **`AiServiceApplication`**: `@HiltAndroidApp`, starts `AiHttpServer` on app start
- **`MainActivity`**: single-activity host for the Compose nav graph
- **`AiBackgroundService`**: foreground service that keeps the server alive when clients are active; shows a persistent notification
- **Hilt modules**: binds `LiteRtRuntimeProvider` to `RuntimeProvider`
- **`AndroidManifest.xml`**: declares permissions, foreground service, provider

Dependencies: all modules

---

### `:demo`
Separate Android application (different `applicationId`). Exercises the full API.

- **Retrofit client** generated from the API schema
- **Screens** for each endpoint: health, create session, generate text, stream tokens, upload audio
- **Token input** field to configure bearer token
- **Log view** showing raw HTTP request/response

Dependencies: `:core:domain` (shared DTOs only), Retrofit, OkHttp, Compose

---

## Dependency Graph

```
:app
 ├── :ui
 │    └── :core:domain
 │    └── :core:data
 ├── :server
 │    └── :core:domain
 ├── :runtime:litert
 │    └── :core:domain
 └── :core:data
      └── :core:domain

:demo (separate application, no shared modules except :core:domain DTOs)
```

---

## `settings.gradle.kts`

```kotlin
include(":app")
include(":core:domain")
include(":core:data")
include(":server")
include(":runtime:litert")
include(":ui")
include(":demo")
```

---

## `libs.versions.toml` — Key Versions

```toml
[versions]
agp                   = "8.8.2"
kotlin                = "2.2.0"
compose-bom           = "2026.02.00"
hilt                  = "2.52"
ktor                  = "3.1.3"
room                  = "2.7.1"
datastore             = "1.1.7"
work                  = "2.10.0"
litertlm              = "0.10.0"
kotlinx-serialization = "1.8.1"
coroutines            = "1.10.2"
mockk                 = "1.14.2"
turbine               = "1.2.0"
junit5                = "5.11.4"

[libraries]
# AI inference
litertlm-android      = { module = "com.google.ai.edge.litert:litert-lm-android",      version.ref = "litertlm" }

# HTTP server
ktor-server-core      = { module = "io.ktor:ktor-server-core",                         version.ref = "ktor" }
ktor-server-cio       = { module = "io.ktor:ktor-server-cio",                          version.ref = "ktor" }
ktor-server-content   = { module = "io.ktor:ktor-server-content-negotiation",          version.ref = "ktor" }
ktor-server-sse       = { module = "io.ktor:ktor-server-sse",                          version.ref = "ktor" }
ktor-serialization    = { module = "io.ktor:ktor-serialization-kotlinx-json",          version.ref = "ktor" }
ktor-server-test      = { module = "io.ktor:ktor-server-test-host",                    version.ref = "ktor" }

# Persistence
room-runtime          = { module = "androidx.room:room-runtime",                       version.ref = "room" }
room-ktx              = { module = "androidx.room:room-ktx",                           version.ref = "room" }
room-compiler         = { module = "androidx.room:room-compiler",                      version.ref = "room" }
datastore-prefs       = { module = "androidx.datastore:datastore-preferences",         version.ref = "datastore" }

# Background work
work-runtime-ktx      = { module = "androidx.work:work-runtime-ktx",                  version.ref = "work" }

# Testing
junit5-api            = { module = "org.junit.jupiter:junit-jupiter-api",              version.ref = "junit5" }
junit5-engine         = { module = "org.junit.jupiter:junit-jupiter-engine",           version.ref = "junit5" }
mockk                 = { module = "io.mockk:mockk",                                   version.ref = "mockk" }
turbine               = { module = "app.cash.turbine:turbine",                         version.ref = "turbine" }
coroutines-test       = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test",    version.ref = "coroutines" }
```

---

## SDK Targets

```kotlin
minSdk     = 31   // matches Edge Gallery; LiteRT-LM requires >= 31
targetSdk  = 36
compileSdk = 36
```

---

## Testing per Module

| Module | Test type | Location |
|--------|-----------|----------|
| `:core:domain` | JUnit 5 unit | `src/test/` |
| `:core:data` | JUnit 5 + Room in-memory | `src/test/` |
| `:server` | Ktor `testApplication` | `src/test/` |
| `:runtime:litert` | Instrumented (needs hardware) | `src/androidTest/` |
| `:ui` | Compose UI tests | `src/androidTest/` |
