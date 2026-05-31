/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.logging

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

class PersistentLogging
private constructor(
    private val context: Context,
    val logBuffer: LogBuffer,
) {
  val entries: StateFlow<List<app.altio.service.domain.debug.LogEntry>> = logBuffer.entries

  fun install(debugBuild: Boolean) {
    if (debugBuild) {
      Timber.plant(Timber.DebugTree())
      Timber.plant(logBuffer)
      CrashHandler.install(context)
      CrashHandler.consumePreviousCrashReport(context)?.let { report ->
        logBuffer.persistRecoveredCrash(report)
        Timber.tag("CrashHandler").e("Previous crash recovered:\n%s", report)
      }
    }
  }

  fun clear() = logBuffer.clear()

  companion object {
    fun create(
        context: Context,
        scope: CoroutineScope,
        databaseName: String,
    ): PersistentLogging {
      val appContext = context.applicationContext
      val database =
          Room.databaseBuilder(appContext, LoggingDatabase::class.java, databaseName)
              .fallbackToDestructiveMigration(true)
              .build()
      return PersistentLogging(
          context = appContext,
          logBuffer = LogBuffer(database.logEntryDao(), scope),
      )
    }
  }
}
