# Altio Service — Master Plan

An on-device Android AI daemon that exposes a loopback HTTP API so any Android app can access Gemma inference (text, chat, audio) without bundling its own model runtime.

---

## Core Principles

- **HTTP-first contract** — REST + streaming over 127.0.0.1, no AIDL
- **Backend-agnostic internals** — Gemma/LiteRT is one implementation behind an abstraction
- **Session isolation** — shared model, independent conversations
- **Modern Android stack** — Kotlin, Coroutines, Compose, Material 3, Hilt, Room, DataStore
- **Tested from day one** — unit tests for every core component, integration tests for the HTTP API

---

## Document Index

| # | Document | What it covers |
|---|----------|----------------|
| 01 | [Module Architecture](01-module-architecture.md) | Multi-module Gradle layout, responsibilities, dependency graph |
| 02 | [API Design](02-api-design.md) | All HTTP endpoints, schemas, streaming format, errors |
| 03 | [Model Management](03-model-management.md) | Download, storage, verification, lifecycle |
| 04 | [Runtime Abstraction](04-runtime-abstraction.md) | Provider interface, LiteRT-LM implementation |
| 05 | [HTTP Server](05-http-server.md) | Ktor setup, loopback binding, middleware |
| 06 | [Session & Scheduling](06-session-scheduling.md) | Session model, job queue, concurrency, cancellation |
| 07 | [Security](07-security.md) | Token auth, input validation, rate limiting |
| 08 | [UI & Setup Flow](08-ui-setup-flow.md) | Compose screens, model download UI, service control |
| 09 | [Testing Strategy](09-testing-strategy.md) | Unit, integration, instrumented tests; demo app |
| 10 | [Phased Execution](10-phased-execution.md) | Implementation phases with clear deliverables |

---

## Technology Decisions

| Concern | Choice | Rationale |
|---------|--------|-----------|
| HTTP server | Ktor Server (CIO engine) | Kotlin-native, coroutine-first, embeddable on Android |
| LLM inference | LiteRT-LM (`litertlm-android`) | Same SDK used by Edge Gallery for Gemma-3n; supports text, vision, audio |
| Model download | WorkManager + CoroutineWorker | Reliable background work, foreground service, resumable HTTP range requests |
| DI | Hilt | Standard Android DI, good Compose integration |
| Async | Coroutines + Flow | Native Kotlin async; Ktor and LiteRT-LM both expose Flow-friendly APIs |
| JSON | Kotlinx Serialization | Compile-time safe, Ktor-native |
| Settings | DataStore (Preferences) | Coroutine-friendly, replaces SharedPreferences |
| Job/Session state | Room | Structured persistence, query-friendly |
| UI | Jetpack Compose + Material 3 | Modern declarative UI |
| Unit tests | JUnit 5 + MockK + Turbine | Idiomatic Kotlin test stack |
| API tests | Ktor `testApplication` | In-process HTTP testing, no emulator needed |

---

## High-Level Architecture

```
┌────────────────────────────────────────────────────────┐
│                   :app (host process)                   │
│                                                        │
│  ┌──────────┐  ┌───────────┐  ┌────────────────────┐  │
│  │  :ui     │  │  :server  │  │  :runtime:litert   │  │
│  │ (Compose)│  │  (Ktor)   │  │  (LiteRT-LM)       │  │
│  └────┬─────┘  └─────┬─────┘  └────────┬───────────┘  │
│       │              │                 │               │
│       └──────────────┼─────────────────┘               │
│                      │                                 │
│              ┌───────┴────────┐                        │
│              │  :core:domain  │                        │
│              │  (interfaces)  │                        │
│              └───────┬────────┘                        │
│                      │                                 │
│              ┌───────┴────────┐                        │
│              │  :core:data    │                        │
│              │ (Room, DS, DL) │                        │
│              └────────────────┘                        │
└────────────────────────────────────────────────────────┘
                       ▲
         HTTP 127.0.0.1:<port>  (loopback only)
                       │
              ┌────────┴────────┐
              │   :demo app     │
              │  (test client)  │
              └─────────────────┘
```

---

## Phased Summary

| Phase | Focus | Key Deliverable |
|-------|-------|-----------------|
| 1 | Foundation | Multi-module Gradle, CI, lint, `/v1/health` endpoint |
| 2 | Model management | Download + verify Gemma model; UI progress screen |
| 3 | Runtime integration | LiteRT-LM engine init; blocking text inference |
| 4 | Sessions & jobs | Session CRUD, job queue, async job pattern |
| 5 | Streaming | SSE streaming tokens to client |
| 6 | Audio | MP3 upload → transcription via LiteRT audio backend |
| 7 | Security | Bearer tokens, rate limiting, input validation |
| 8 | UI polish | Full setup flow, model manager, service dashboard |
| 9 | Demo app | Standalone app exercising all API endpoints |
| 10 | Hardening | Edge cases, memory pressure, idle shutdown, docs |
