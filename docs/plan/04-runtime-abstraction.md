# 04 — Runtime Abstraction

## Goals

- The HTTP API and session layer must never reference LiteRT-LM types directly
- Swapping in a new backend (e.g. ONNX, ExecuTorch) only requires a new `:runtime:X` module and a Hilt binding change
- The abstraction must support streaming (token-by-token), cancellation, and multimodal input (text, image, audio)

---

## Core Interfaces (`:core:domain`)

### `RuntimeProvider`

```kotlin
interface RuntimeProvider {
    /** Called once when the model file is ready. Initializes the engine. */
    suspend fun load(modelPath: String, config: RuntimeConfig): RuntimeEngine

    /** Returns true if this provider can handle the given model. */
    fun supports(modelId: String): Boolean
}
```

### `RuntimeConfig`

```kotlin
data class RuntimeConfig(
    val accelerator: Accelerator = Accelerator.AUTO,
    val maxTokens: Int = 2048,
    val numThreads: Int = 4,
)

enum class Accelerator { AUTO, CPU, GPU, NPU }
```

### `RuntimeEngine`

```kotlin
interface RuntimeEngine : Closeable {
    /** Creates an isolated session (conversation). */
    suspend fun createSession(params: SessionParams): RuntimeSession

    /** Frees all native resources. */
    override fun close()
}
```

### `RuntimeSession`

```kotlin
interface RuntimeSession : Closeable {
    val sessionId: String

    /**
     * Runs inference and emits tokens as a Flow.
     * The flow completes when generation finishes or is cancelled.
     */
    fun generateStream(request: InferenceRequest): Flow<InferenceChunk>

    /** Runs audio transcription. Returns final result (no streaming in v1). */
    suspend fun transcribe(audioBytes: ByteArray, mimeType: String): TranscriptionResult

    /** Resets conversation history while keeping the session open. */
    suspend fun reset()

    override fun close()
}
```

### `InferenceRequest`

```kotlin
data class InferenceRequest(
    val messages: List<Message>,
    val config: GenerationConfig = GenerationConfig(),
)

data class Message(
    val role: Role,
    val parts: List<Part>,
)

enum class Role { SYSTEM, USER, MODEL }

sealed class Part {
    data class Text(val text: String) : Part()
    data class Image(val pngBytes: ByteArray) : Part()
    data class Audio(val rawBytes: ByteArray) : Part()
}

data class GenerationConfig(
    val temperature: Float = 0.8f,
    val topK: Int = 64,
    val topP: Float = 0.95f,
    val maxTokens: Int = 2048,
)
```

### `InferenceChunk`

```kotlin
sealed class InferenceChunk {
    data class Token(val text: String, val index: Int) : InferenceChunk()
    data class Done(val finishReason: FinishReason, val usage: TokenUsage) : InferenceChunk()
    data class Error(val cause: Throwable) : InferenceChunk()
}

enum class FinishReason { STOP, MAX_TOKENS, CANCELLED, ERROR }

data class TokenUsage(val promptTokens: Int, val completionTokens: Int)
```

### `TranscriptionResult`

```kotlin
data class TranscriptionResult(
    val transcript: String,
    val language: String,
    val durationMs: Long,
)
```

---

## LiteRT-LM Implementation (`:runtime:litert`)

Based on Edge Gallery's `LlmChatModelHelper.kt`.

### `LiteRtRuntimeProvider`

```kotlin
@Singleton
class LiteRtRuntimeProvider @Inject constructor() : RuntimeProvider {

    override fun supports(modelId: String) = true // v1 only has litert models

    override suspend fun load(modelPath: String, config: RuntimeConfig): RuntimeEngine {
        val engineConfig = EngineConfig.newBuilder()
            .setModelPath(modelPath)
            .setPreferredBackend(config.accelerator.toLiteRtBackend())
            .setMaxNumTokens(config.maxTokens)
            .build()
        val engine = Engine.createFromConfig(engineConfig) // blocking native call
        return LiteRtRuntimeEngine(engine, config)
    }
}
```

### `LiteRtRuntimeEngine`

```kotlin
class LiteRtRuntimeEngine(
    private val engine: Engine,
    private val config: RuntimeConfig,
) : RuntimeEngine {

    override suspend fun createSession(params: SessionParams): RuntimeSession {
        val conversationConfig = ConversationConfig.newBuilder()
            .setSamplerConfig(
                SamplerConfig.newBuilder()
                    .setTemperature(params.generationConfig.temperature)
                    .setTopK(params.generationConfig.topK)
                    .setTopP(params.generationConfig.topP)
                    .build()
            )
            .apply { params.systemPrompt?.let { setSystemInstruction(it) } }
            .build()
        val conversation = engine.createConversation(conversationConfig)
        return LiteRtRuntimeSession(UUID.randomUUID().toString(), conversation)
    }

    override fun close() = engine.close()
}
```

### `LiteRtRuntimeSession`

```kotlin
class LiteRtRuntimeSession(
    override val sessionId: String,
    private val conversation: Conversation,
) : RuntimeSession {

    override fun generateStream(request: InferenceRequest): Flow<InferenceChunk> = callbackFlow {
        val inputBuilder = InferenceInput.newBuilder()
        request.messages.lastOrNull { it.role == Role.USER }?.parts?.forEach { part ->
            when (part) {
                is Part.Text  -> inputBuilder.setPrompt(part.text)
                is Part.Image -> inputBuilder.setImage(part.pngBytes)
                is Part.Audio -> inputBuilder.setAudio(part.rawBytes)
            }
        }

        var tokenIndex = 0
        conversation.sendMessageAsync(
            inputBuilder.build(),
            object : MessageCallback {
                override fun onMessage(partialResult: String, done: Boolean) {
                    trySend(InferenceChunk.Token(partialResult, tokenIndex++))
                    if (done) {
                        trySend(InferenceChunk.Done(FinishReason.STOP, TokenUsage(0, tokenIndex)))
                        close()
                    }
                }
                override fun onError(e: Exception) {
                    close(e)
                }
            }
        )
        awaitClose { conversation.stopGeneration() }
    }

    override suspend fun transcribe(audioBytes: ByteArray, mimeType: String): TranscriptionResult {
        // LiteRT-LM audio: submit audio bytes as an audio-only inference
        // Returns full transcript in one call (no streaming in v1)
        val result = withContext(Dispatchers.IO) {
            val input = InferenceInput.newBuilder().setAudio(audioBytes).build()
            conversation.runBlocking(input) // synchronous call
        }
        return TranscriptionResult(
            transcript = result.text,
            language = "en",
            durationMs = result.durationMs,
        )
    }

    override suspend fun reset() = conversation.reset()

    override fun close() = conversation.close()
}
```

---

## Accelerator Mapping

```kotlin
fun Accelerator.toLiteRtBackend(): Backend = when (this) {
    Accelerator.CPU  -> Backend.CPU
    Accelerator.GPU  -> Backend.GPU
    Accelerator.NPU  -> Backend.NPU
    Accelerator.AUTO -> Backend.GPU  // prefer GPU, fall back handled by LiteRT internally
}
```

---

## Hilt Binding (`:app`)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RuntimeModule {
    @Binds
    @Singleton
    abstract fun bindRuntimeProvider(impl: LiteRtRuntimeProvider): RuntimeProvider
}
```

Adding a second backend later only requires:
- A new `:runtime:onnx` module implementing `RuntimeProvider`
- A Hilt `@IntoMap` + qualifier pattern for multi-provider selection

---

## Unit Tests (`:core:domain`)

All tests run on JVM — no Android device needed.

```kotlin
class FakeRuntimeSession(override val sessionId: String = "fake-session") : RuntimeSession {
    var resetCount = 0
    var closed = false
    var tokensToEmit = listOf("Hello", " world")

    override fun generateStream(request: InferenceRequest): Flow<InferenceChunk> = flow {
        tokensToEmit.forEachIndexed { i, t -> emit(InferenceChunk.Token(t, i)) }
        emit(InferenceChunk.Done(FinishReason.STOP, TokenUsage(5, tokensToEmit.size)))
    }
    override suspend fun transcribe(audioBytes: ByteArray, mimeType: String) =
        TranscriptionResult("fake transcript", "en", 1000L)
    override suspend fun reset() { resetCount++ }
    override fun close() { closed = true }
}
```

- `GenerateStreamTest`: assert token sequence, `Done` at end, cancellation mid-stream
- `SessionIsolationTest`: two sessions from same engine get independent history
