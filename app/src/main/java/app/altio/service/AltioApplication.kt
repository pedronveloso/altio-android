/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service

import android.app.Application
import androidx.work.Configuration
import app.altio.service.domain.job.JOB_INTERRUPTED_ERROR_CODE
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.android.MetroApplication
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class AltioApplication : Application(), MetroApplication, Configuration.Provider {
  val appGraph: AppGraph by lazy { createGraphFactory<AppGraph.Factory>().create(this) }
  private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override val appComponentProviders: MetroAppComponentProviders
    get() = appGraph

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().setWorkerFactory(appGraph.workerFactory).build()

  override fun onCreate() {
    super.onCreate()
    appGraph.logging.install(debugBuild = BuildConfig.DEBUG)
    startupScope.launch {
      runCatching { appGraph.legacyStateImporter.importIfNeeded() }
          .onFailure { Timber.e(it, "Legacy state import failed during app startup") }
      Timber.i("Reconciling stale active jobs on app startup")
      runCatching { appGraph.jobRepository.failActiveJobsOnStartup(Instant.now()) }
          .onSuccess { count ->
            if (count > 0) {
              Timber.i(
                  "Failed %d stale active jobs with error code %s",
                  count,
                  JOB_INTERRUPTED_ERROR_CODE,
              )
            }
          }
          .onFailure {
            Timber.e(it, "SVC008 Failed reconciling stale active jobs during app startup")
          }
      runCatching { appGraph.sessionCoordinator.deleteAllSessions() }
          .onFailure { Timber.e(it, "Failed clearing persisted sessions during cold start") }
      runCatching { appGraph.modelRepository.reconcileModelsOnStartup() }
          .onFailure { Timber.e(it, "Model reconciliation failed during app startup") }
    }
    // Server lifecycle is managed by AiBackgroundService.
    // MainActivity starts the service on first launch after setup completes.
  }
}
