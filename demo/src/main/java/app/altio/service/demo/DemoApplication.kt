/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.demo

import android.app.Application
import app.altio.service.logging.PersistentLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DemoApplication : Application() {
  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  val logging: PersistentLogging by lazy {
    PersistentLogging.create(this, appScope, "demo_logs.db")
  }

  override fun onCreate() {
    super.onCreate()
    logging.install(debugBuild = BuildConfig.DEBUG)
  }
}
