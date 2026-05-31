/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.model

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import app.altio.service.data.db.ModelDao
import app.altio.service.data.db.ModelEntity
import app.altio.service.data.download.Sha256Verifier
import app.altio.service.domain.model.ModelStatus
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.File
import java.io.RandomAccessFile
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ModelRepositoryImplTest {

  private val workManager: WorkManager = mockk(relaxed = true)
  private val fakeDao = FakeModelDao()
  private val fakeContext = mockk<android.content.Context>(relaxed = true)
  private lateinit var repository: ModelRepositoryImpl

  @BeforeEach
  fun setUp() {
    every { fakeContext.getExternalFilesDir(null) } returns null
    repository = ModelRepositoryImpl(fakeDao, workManager, fakeContext)
  }

  @Test
  fun `pauseDownload cancels WorkManager unique work`() = runTest {
    fakeDao.statusOverride = ModelStatus.DOWNLOADING
    repository.pauseDownload("gemma-4-e2b-it")
    verify { workManager.cancelUniqueWork("download:gemma-4-e2b-it") }
  }

  @Test
  fun `pauseDownload sets DB status to PAUSED`() = runTest {
    repository.pauseDownload("gemma-4-e2b-it")
    assert(fakeDao.lastUpdatedStatus == ModelStatus.PAUSED) {
      "Expected PAUSED but was ${fakeDao.lastUpdatedStatus}"
    }
  }

  @Test
  fun `resumeDownload enqueues with REPLACE policy not KEEP`() = runTest {
    repository.resumeDownload("gemma-4-e2b-it")
    // Capture the enqueue call — REPLACE is critical because WM still holds the old CANCELLED
    // record and KEEP would silently no-op, leaving the download stuck.
    coVerify {
      workManager.enqueueUniqueWork(
          "download:gemma-4-e2b-it",
          ExistingWorkPolicy.REPLACE,
          any<OneTimeWorkRequest>(),
      )
    }
  }

  @Test
  fun `resumeDownload sets DB status to DOWNLOADING before enqueue`() = runTest {
    repository.resumeDownload("gemma-4-e2b-it")
    assert(fakeDao.lastUpdatedStatus == ModelStatus.DOWNLOADING) {
      "Expected DOWNLOADING but was ${fakeDao.lastUpdatedStatus}"
    }
  }

  @Test
  fun `cancelDownload sets DB status to NOT_DOWNLOADED`() = runTest {
    repository.cancelDownload("gemma-4-e2b-it")
    assert(fakeDao.lastUpdatedStatus == ModelStatus.NOT_DOWNLOADED) {
      "Expected NOT_DOWNLOADED but was ${fakeDao.lastUpdatedStatus}"
    }
  }

  @Test
  fun `cancelDownload deletes partial tmp file if present`() = runTest {
    val tempDir = createTempDirectory().toFile()
    every { fakeContext.getExternalFilesDir(null) } returns tempDir
    val tmpFile = File(tempDir, "models/gemma-4-e2b-it/gemma-4-E2B-it.litertlm.tmp")
    tmpFile.parentFile?.mkdirs()
    tmpFile.createNewFile()

    repository.cancelDownload("gemma-4-e2b-it")

    assert(!tmpFile.exists()) { "Expected tmp file to be deleted after cancel" }
    tempDir.deleteRecursively()
  }

  @Test
  fun `cancelDownload is safe when no partial tmp file exists`() = runTest {
    val tempDir = createTempDirectory().toFile()
    every { fakeContext.getExternalFilesDir(null) } returns tempDir

    // No tmp file created — should not throw
    repository.cancelDownload("gemma-4-e2b-it")

    assert(fakeDao.lastUpdatedStatus == ModelStatus.NOT_DOWNLOADED) {
      "Expected NOT_DOWNLOADED but was ${fakeDao.lastUpdatedStatus}"
    }
    tempDir.deleteRecursively()
  }

  @Test
  fun `built in demo model is always ready`() = runTest {
    val model = repository.getModel("demo-model").first()

    assertEquals(ModelStatus.READY, model?.status)
    assertEquals(null, model?.filePath)
  }

  @Test
  fun `deleteModel removes downloaded file and db record`() = runTest {
    val tempDir = createTempDirectory().toFile()
    val modelFile = File(tempDir, "models/gemma-4-e2b-it/version/model.bin")
    modelFile.parentFile?.mkdirs()
    modelFile.writeText("content")
    fakeDao.entity =
        ModelEntity(
            id = "gemma-4-e2b-it",
            name = "Gemma",
            version = "version",
            sizeBytes = 1L,
            status = ModelStatus.READY,
            downloadedAt = 1L,
            filePath = modelFile.absolutePath,
        )

    repository.deleteModel("gemma-4-e2b-it")

    assertFalse(modelFile.exists())
    assertTrue(fakeDao.deletedIds.contains("gemma-4-e2b-it"))
    tempDir.deleteRecursively()
  }

  @Test
  fun `verifyModel uses persisted file path`() = runTest {
    val tempDir = createTempDirectory().toFile()
    every { fakeContext.getExternalFilesDir(null) } returns tempDir
    val modelFile = File(tempDir, "verified-model.litertlm")
    val bytes = "verified content".toByteArray()
    modelFile.writeBytes(bytes)
    fakeDao.entity =
        ModelEntity(
            id = "gemma-4-e2b-it",
            name = "Gemma",
            version = "version",
            sizeBytes = bytes.size.toLong(),
            status = ModelStatus.READY,
            downloadedAt = 1L,
            filePath = modelFile.absolutePath,
        )

    mockkObject(Sha256Verifier)
    val fileSlot = slot<File>()
    every { Sha256Verifier.verify(capture(fileSlot), any()) } returns true

    assertTrue(repository.verifyModel("gemma-4-e2b-it"))
    assertEquals(modelFile.absolutePath, fileSlot.captured.absolutePath)

    unmockkObject(Sha256Verifier)
    tempDir.deleteRecursively()
  }

  @Test
  fun `reconcileModelsOnStartup restores ready model from disk when db is empty`() = runTest {
    val tempDir = createTempDirectory().toFile()
    every { fakeContext.getExternalFilesDir(null) } returns tempDir
    val modelFile =
        File(
            tempDir,
            "models/gemma-4-e2b-it/${app.altio.service.data.catalog.ModelCatalog.GEMMA_4_E2B_IT_VERSION}/gemma-4-E2B-it.litertlm",
        )
    modelFile.parentFile?.mkdirs()
    RandomAccessFile(modelFile, "rw").use { it.setLength(2_588_147_712L) }
    modelFile.setLastModified(1234L)

    repository.reconcileModelsOnStartup()

    assertEquals("gemma-4-e2b-it", fakeDao.upsertedEntity?.id)
    assertEquals(ModelStatus.READY, fakeDao.upsertedEntity?.status)
    assertEquals(modelFile.absolutePath, fakeDao.upsertedEntity?.filePath)
    assertEquals(1234L, fakeDao.upsertedEntity?.downloadedAt)
    tempDir.deleteRecursively()
  }

  @Test
  fun `reconcileModelsOnStartup invalidates old gemma install and deletes legacy directory`() =
      runTest {
        val tempDir = createTempDirectory().toFile()
        every { fakeContext.getExternalFilesDir(null) } returns tempDir
        val legacyModelFile =
            File(
                tempDir,
                "models/gemma-4-e2b-it/${app.altio.service.data.catalog.ModelCatalog.GEMMA_4_E2B_IT_LEGACY_VERSION}/gemma-4-E2B-it.litertlm",
            )
        legacyModelFile.parentFile?.mkdirs()
        RandomAccessFile(legacyModelFile, "rw").use { it.setLength(1L) }
        fakeDao.entity =
            ModelEntity(
                id = "gemma-4-e2b-it",
                name = "Gemma",
                version = app.altio.service.data.catalog.ModelCatalog.GEMMA_4_E2B_IT_LEGACY_VERSION,
                sizeBytes = 1L,
                status = ModelStatus.READY,
                downloadedAt = 1L,
                filePath = legacyModelFile.absolutePath,
            )

        repository.reconcileModelsOnStartup()

        assertEquals("gemma-4-e2b-it", fakeDao.clearedId)
        assertEquals(ModelStatus.NOT_DOWNLOADED, fakeDao.clearedStatus)
        assertFalse(legacyModelFile.parentFile!!.exists())
        tempDir.deleteRecursively()
      }

  @Test
  fun `reconcileModelsOnStartup clears stale ready metadata when file is missing`() = runTest {
    val tempDir = createTempDirectory().toFile()
    every { fakeContext.getExternalFilesDir(null) } returns tempDir
    fakeDao.entity =
        ModelEntity(
            id = "gemma-4-e2b-it",
            name = "Gemma",
            version = "version",
            sizeBytes = 1L,
            status = ModelStatus.READY,
            downloadedAt = 1L,
            filePath = "/missing/model.bin",
        )

    repository.reconcileModelsOnStartup()

    assertEquals("gemma-4-e2b-it", fakeDao.clearedId)
    assertEquals(ModelStatus.NOT_DOWNLOADED, fakeDao.clearedStatus)
    tempDir.deleteRecursively()
  }

  // ── Fake ──────────────────────────────────────────────────────────────────

  private class FakeModelDao : ModelDao {
    var lastUpdatedStatus: ModelStatus? = null
    var statusOverride: ModelStatus? = null
    var entity: ModelEntity? = null
    var upsertedEntity: ModelEntity? = null
    var clearedId: String? = null
    var clearedStatus: ModelStatus? = null
    val deletedIds = mutableListOf<String>()

    override fun observeAll(): Flow<List<ModelEntity>> = emptyFlow()

    override fun observe(id: String): Flow<ModelEntity?> = flowOf(entity?.takeIf { it.id == id })

    override suspend fun getAll(): List<ModelEntity> = listOfNotNull(entity)

    override suspend fun countRows(): Int = listOfNotNull(entity).size

    override suspend fun upsert(entity: ModelEntity) {
      upsertedEntity = entity
      this.entity = entity
    }

    override suspend fun updateStatus(id: String, status: ModelStatus) {
      lastUpdatedStatus = status
    }

    override suspend fun updateReady(
        id: String,
        status: ModelStatus,
        filePath: String,
        downloadedAt: Long,
    ) {}

    override suspend fun clearDownloadedState(id: String, status: ModelStatus) {
      clearedId = id
      clearedStatus = status
      if (entity?.id == id) {
        entity = entity?.copy(status = status, filePath = null, downloadedAt = null)
      }
    }

    override suspend fun delete(id: String) {
      deletedIds += id
      if (entity?.id == id) entity = null
    }
  }
}
