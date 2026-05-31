# 06 — Session & Scheduling

## Three-Layer State Model

```
Model runtime state   — Engine + loaded weights (shared across all sessions)
Session state         — Conversation history per session per client
Job state             — Execution progress and output for a single request
```

These are kept strictly separate. Sharing a model never implies sharing conversation history.

---

## Entities (`:core:domain`)

### Session

```kotlin
data class Session(
    val id: String,               // "ses_<uuid>"
    val clientId: String,         // derived from bearer token
    val modelId: String,
    val systemPrompt: String?,
    val generationConfig: GenerationConfig,
    val messageCount: Int,
    val createdAt: Instant,
    val lastActiveAt: Instant,
)
```

### Job

```kotlin
data class Job(
    val id: String,               // "job_<uuid>"
    val sessionId: String,
    val clientId: String,
    val type: JobType,            // GENERATE, TRANSCRIBE
    val status: JobStatus,
    val inputSummary: String,     // truncated prompt for diagnostics
    val result: JobResult?,
    val createdAt: Instant,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val errorCode: String?,
)

enum class JobStatus { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED }
enum class JobType { GENERATE, TRANSCRIBE }

sealed class JobResult {
    data class Generation(
        val content: String,
        val finishReason: FinishReason,
        val usage: TokenUsage,
    ) : JobResult()

    data class Transcription(
        val transcript: String,
        val language: String,
        val durationMs: Long,
    ) : JobResult()
}
```

---

## Room Schema (`:core:data`)

```kotlin
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val clientId: String,
    val modelId: String,
    val systemPrompt: String?,
    val generationConfigJson: String,  // JSON-encoded GenerationConfig
    val messageCount: Int,
    val createdAt: Long,
    val lastActiveAt: Long,
)

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val clientId: String,
    val type: String,
    val status: String,
    val inputSummary: String,
    val resultJson: String?,
    val createdAt: Long,
    val startedAt: Long?,
    val completedAt: Long?,
    val errorCode: String?,
)
```

---

## `InferenceScheduler`

Lives in `:core:data` (or `:server`). Owns the job queue and enforces concurrency limits.

```kotlin
@Singleton
class InferenceScheduler @Inject constructor(
    private val runtimeEngine: RuntimeEngineHolder,
    private val sessionRepo: SessionRepository,
    private val jobRepo: JobRepository,
    private val scope: CoroutineScope,  // app-scoped
) {
    // v1: max 1 concurrent inference job (LiteRT-LM is typically single-threaded per engine)
    private val semaphore = Semaphore(1)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    fun submitJob(jobId: String, sessionId: String, request: InferenceRequest): Flow<InferenceChunk> {
        return channelFlow {
            jobRepo.updateStatus(jobId, JobStatus.QUEUED)
            semaphore.acquire()
            try {
                jobRepo.updateStatus(jobId, JobStatus.RUNNING)
                val session = runtimeEngine.getSession(sessionId)
                    ?: throw AiServiceException("SESSION_NOT_FOUND")

                session.generateStream(request)
                    .onEach { chunk -> send(chunk) }
                    .onCompletion { cause ->
                        if (cause == null) {
                            jobRepo.updateStatus(jobId, JobStatus.COMPLETED)
                        } else if (cause is CancellationException) {
                            jobRepo.updateStatus(jobId, JobStatus.CANCELLED)
                        } else {
                            jobRepo.updateStatus(jobId, JobStatus.FAILED, cause.message)
                        }
                    }
                    .collect()
            } finally {
                semaphore.release()
                activeJobs.remove(jobId)
            }
        }.shareIn(scope, SharingStarted.Eagerly, replay = Int.MAX_VALUE)
         // replay = Int.MAX_VALUE so late SSE subscribers catch up on missed tokens
    }

    fun cancelJob(jobId: String) {
        activeJobs[jobId]?.let { scope.launch { /* cancel coroutine */ } }
        jobRepo.updateStatus(jobId, JobStatus.CANCELLED)
    }
}
```

### Replay Buffer for SSE

Since the SSE stream (`GET /v1/jobs/{id}/stream`) may be opened after the job starts, the scheduler uses `shareIn` with full replay. Each new subscriber receives all tokens emitted so far, then follows live.

---

## Session Repository Interface

```kotlin
interface SessionRepository {
    suspend fun createSession(params: CreateSessionParams): Session
    fun getSession(id: String): Flow<Session?>
    suspend fun resetSession(id: String)
    suspend fun deleteSession(id: String)
    fun getSessionsByClient(clientId: String): Flow<List<Session>>
}
```

---

## `RuntimeEngineHolder`

Singleton that keeps the loaded `RuntimeEngine` and all active `RuntimeSession`s:

```kotlin
@Singleton
class RuntimeEngineHolder @Inject constructor(
    private val runtimeProvider: RuntimeProvider,
) {
    private var engine: RuntimeEngine? = null
    private val sessions = ConcurrentHashMap<String, RuntimeSession>()
    private val lock = Mutex()

    suspend fun ensureLoaded(modelPath: String, config: RuntimeConfig) = lock.withLock {
        if (engine == null) {
            engine = runtimeProvider.load(modelPath, config)
        }
    }

    suspend fun createSession(sessionId: String, params: SessionParams): RuntimeSession = lock.withLock {
        val eng = engine ?: error("Engine not loaded")
        val session = eng.createSession(params)
        sessions[sessionId] = session
        session
    }

    fun getSession(sessionId: String): RuntimeSession? = sessions[sessionId]

    suspend fun removeSession(sessionId: String) {
        sessions.remove(sessionId)?.close()
    }

    suspend fun unload() = lock.withLock {
        sessions.values.forEach { it.close() }
        sessions.clear()
        engine?.close()
        engine = null
    }
}
```

---

## Concurrency Policy (v1)

| Scenario | Behavior |
|----------|----------|
| Two clients generate simultaneously | Second job queues; runs after first completes |
| Max concurrent jobs per token | 5 (validated in HTTP handler before scheduling) |
| Max sessions per token | 20 (validated in session creation handler) |
| Job queue depth | Unbounded in v1; future: configurable cap |
| Transcription + generation | Transcription also acquires the semaphore (same engine) |

The semaphore limit (1) can be raised later if LiteRT-LM supports parallel sessions, without changing the public API.

---

## Cancellation Flow

1. Client calls `POST /v1/jobs/{id}/cancel`
2. `JobHandler` calls `scheduler.cancelJob(jobId)`
3. Scheduler cancels the coroutine running that job
4. LiteRT-LM's `stopGeneration()` is called inside `awaitClose` of the `callbackFlow`
5. The `Flow` completes with `CancellationException`
6. Job status updated to `CANCELLED` in Room
7. SSE stream emits a final `event: error` with code `CANCELLED` then closes

---

## Idle Session Cleanup

A periodic job runs every minute and closes sessions inactive for > 30 minutes:

```kotlin
scope.launch {
    while (true) {
        delay(60_000)
        val cutoff = Instant.now().minusSeconds(1800)
        sessionRepo.getStaleSessionIds(cutoff).forEach { id ->
            engineHolder.removeSession(id)
            sessionRepo.deleteSession(id)
        }
    }
}
```

---

## Unit Tests (`:core:domain` / `:core:data`)

- `InferenceSchedulerTest`: two concurrent submissions, verify second waits; cancellation mid-stream
- `SessionRepositoryTest`: create/get/reset/delete with Room in-memory DB
- `RuntimeEngineHolderTest`: load once even when called concurrently; session isolation
