# 03 — Model Management

## Overview

Model management covers the full lifecycle: definition → download → verification → storage → load → unload → delete.
This lives primarily in `:core:data`, with UI in `:ui` and download worker in `:core:data`.

---

## Model Definition

Each available model is described in `assets/models.json` (bundled in `:core:data`):

Canonical upstream source for model references: https://huggingface.co/litert-community

When updating model IDs, repository names, file names, version identifiers, or checksums, use that
page as the authoritative source.

```json
{
  "models": [
    {
      "id": "gemma-4-e2b-it",
      "name": "Gemma 4 E2B IT",
      "description": "Gemma 4 multimodal: text, vision, audio, thinking. 2.4 GB. Requires 8 GB device RAM.",
      "version": "7fa1d78473894f7e736a21d920c3aa80f950c0db",
      "huggingface_repo": "litert-community/gemma-4-E2B-it-litert-lm",
      "size_bytes": 2583085056,
      "sha256": "<fill-from-hf>",
      "capabilities": ["text", "vision", "audio", "thinking"],
      "min_sdk": 31,
      "min_device_memory_gb": 8,
      "max_context_length": 32000,
      "runtime": "litert-lm",
      "files": [
        {
          "name": "gemma-4-E2B-it.litertlm",
          "size_bytes": 2583085056,
          "sha256": "<fill-from-hf>"
        }
      ],
      "default_config": {
        "top_k": 64,
        "top_p": 0.95,
        "temperature": 1.0,
        "max_tokens": 4000,
        "accelerators": ["gpu", "cpu"],
        "vision_accelerator": "gpu"
      }
    }
  ]
}
```

The `ModelRepository` interface (`:core:domain`) exposes:

```kotlin
interface ModelRepository {
    fun getAvailableModels(): Flow<List<Model>>
    fun getModel(id: String): Flow<Model?>
    suspend fun getDownloadProgress(id: String): Flow<DownloadProgress>
    suspend fun startDownload(id: String)
    suspend fun cancelDownload(id: String)
    suspend fun deleteModel(id: String)
    suspend fun verifyModel(id: String): Boolean
}
```

---

## Storage Layout

```
{externalFilesDir}/models/{modelId}/{version}/{filename}
```

Example:
```
/sdcard/Android/data/app.altio.service/files/models/
  gemma-4-e2b-it/
    7fa1d78.../
      gemma-4-E2B-it.litertlm         ← final file
      gemma-4-E2B-it.litertlm.tmp     ← in-progress download
```

Temp files use `.tmp` extension during download. On completion or verification failure the `.tmp` is either renamed or deleted.

---

## Download Infrastructure

### `ModelDownloadWorker` (extends `CoroutineWorker`)

Runs via WorkManager with `FOREGROUND_SERVICE_TYPE_DATA_SYNC`.

Key behaviors (mirroring Edge Gallery's `DownloadWorker`):
- **Resumable downloads**: sets `Range: bytes=<existingBytes>-` header if `.tmp` already exists
- **Progress reporting**: reports every 200 ms via `setProgress()` with bytes received, total bytes, bytes/sec, ETA ms
- **Download rate**: 5-sample sliding window for bytes/sec calculation
- **Zip support**: streams zip extraction to avoid loading entire archive in memory
- **Retry policy**: `BackoffPolicy.LINEAR` with 30 s initial backoff, max 3 retries
- **Notifications**: foreground notification with percentage, cancel action

### `DownloadRepository` (implements `ModelRepository` download methods)

```kotlin
class DownloadRepository(
    private val workManager: WorkManager,
    private val db: AiServiceDatabase,
) {
    fun startDownload(modelId: String) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf("model_id" to modelId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .addTag("download:$modelId")
            .build()
        workManager.enqueueUniqueWork("download:$modelId", ExistingWorkPolicy.KEEP, request)
    }

    fun getDownloadProgress(modelId: String): Flow<DownloadProgress> =
        workManager
            .getWorkInfosByTagFlow("download:$modelId")
            .map { infos -> infos.firstOrNull()?.toDownloadProgress() }
            .filterNotNull()
}
```

---

## File Verification

After download completes, the worker verifies the file SHA-256 before renaming `.tmp` → final:

```kotlin
suspend fun verifySha256(file: File, expected: String): Boolean {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered(8192).use { stream ->
        val buffer = ByteArray(8192)
        var read: Int
        while (stream.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toHexString() == expected
}
```

---

## Model Status State Machine

```
NOT_DOWNLOADED
     │ startDownload()
     ▼
DOWNLOADING ──(error)──► NOT_DOWNLOADED
     │ complete
     ▼
VERIFYING
     │ sha256 ok
     ▼
READY ──(loadModel())──► LOADING ──► LOADED
     │                                  │
     │ deleteModel()              unloadModel()
     ▼                                  │
NOT_DOWNLOADED ◄──────────────────────┘
```

Model status is persisted in the Room `ModelEntity` table so it survives process restarts.

---

## Room Entity

```kotlin
@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val sizeByes: Long,
    val status: ModelStatus,          // enum: NOT_DOWNLOADED, DOWNLOADING, READY, LOADED
    val downloadedAt: Long?,
    val filePath: String?,
)
```

---

## Model Loading

Loading is handled by `:runtime:litert` but triggered via `ModelRepository.loadModel(id)`.

- Only one model can be `LOADED` at a time in v1
- Loading blocks until the engine is ready or fails
- On success the `ModelEntity.status` is updated to `LOADED`
- Loading is done inside `:app`'s `AiBackgroundService` to keep the process alive

---

## Idle Unloading

A configurable idle timer (default 10 minutes, set in Settings) triggers `unloadModel()` when no job has run for that duration. This is implemented as a `CountDownTimer` reset on every job start.

---

## Unit Tests (`:core:data`)

- `ModelDownloadWorkerTest`: use `TestListenableWorkerBuilder`, mock `OkHttpClient`, assert progress emissions and file rename
- `DownloadRepositoryTest`: use Room in-memory DB + `WorkManagerTestInitHelper`, assert state transitions
- `Sha256VerificationTest`: pure JUnit 5, known-good and corrupted file fixtures
