/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.download

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import app.altio.service.data.db.DurableStateDatabase
import app.altio.service.domain.model.DownloadFailureReason
import app.altio.service.domain.model.ModelStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelDownloadWorkerTest {

  private lateinit var context: Context
  private lateinit var db: DurableStateDatabase
  private lateinit var mockServer: MockWebServer
  private lateinit var httpClient: OkHttpClient
  private lateinit var factory: AppWorkerFactory

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db =
        Room.inMemoryDatabaseBuilder(context, DurableStateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    mockServer = MockWebServer()
    mockServer.start()

    // Redirect all HTTP(S) requests to MockWebServer so tests run offline
    val redirectInterceptor = Interceptor { chain ->
      val original = chain.request()
      val redirected = original.newBuilder().url(mockServer.url(original.url.encodedPath)).build()
      chain.proceed(redirected)
    }
    httpClient = OkHttpClient.Builder().addInterceptor(redirectInterceptor).build()

    factory = AppWorkerFactory(db.modelDao(), httpClient)

    // Initialize WorkManager in test mode so setForeground/setProgress work correctly
    val config =
        Configuration.Builder()
            .setWorkerFactory(factory)
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
    WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
  }

  @After
  fun tearDown() {
    db.close()
    mockServer.shutdown()
  }

  // ─── Input validation ─────────────────────────────────────────────────────

  @Test
  fun doWork_returnsFailure_forUnknownModelId() = runTest {
    val worker = buildWorker(modelId = "not-in-catalog")
    val result = worker.doWork()
    assertTrue(result is ListenableWorker.Result.Failure)
    assertEquals(
        DownloadFailureReason.UNKNOWN_MODEL.name,
        (result as ListenableWorker.Result.Failure)
            .outputData
            .getString(ModelDownloadWorker.KEY_FAILURE_REASON),
    )
  }

  // ─── Network error → retry ────────────────────────────────────────────────

  @Test
  fun doWork_returnsRetry_onNetworkError() = runTest {
    mockServer.enqueue(MockResponse().setResponseCode(503))

    val worker = buildWorker(modelId = CATALOG_MODEL_ID)
    val result = worker.doWork()
    assertEquals(ListenableWorker.Result.retry(), result)
  }

  @Test
  fun doWork_keepsDownloadingStatus_inDb_onNetworkError() = runTest {
    // runAttemptCount defaults to 0, which is < MAX_RETRIES — worker retries and keeps DOWNLOADING.
    mockServer.enqueue(MockResponse().setResponseCode(503))

    val worker = buildWorker(modelId = CATALOG_MODEL_ID)
    val result = worker.doWork()

    assertEquals(ListenableWorker.Result.retry(), result)
    val entity = db.modelDao().observe(CATALOG_MODEL_ID).first()
    assertEquals(ModelStatus.DOWNLOADING, entity?.status)
  }

  @Test
  fun doWork_keepsDownloadingStatus_inDb_on404() = runTest {
    // 404 is not 2xx → IOException → retry path → status stays DOWNLOADING.
    mockServer.enqueue(MockResponse().setResponseCode(404))

    val worker = buildWorker(modelId = CATALOG_MODEL_ID)
    val result = worker.doWork()

    assertEquals(ListenableWorker.Result.retry(), result)
    val entity = db.modelDao().observe(CATALOG_MODEL_ID).first()
    assertEquals(ModelStatus.DOWNLOADING, entity?.status)
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private fun buildWorker(modelId: String): ModelDownloadWorker =
      TestListenableWorkerBuilder<ModelDownloadWorker>(context)
          .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to modelId))
          .setWorkerFactory(factory)
          .build()

  companion object {
    private const val CATALOG_MODEL_ID = "gemma-4-e2b-it"
  }
}
