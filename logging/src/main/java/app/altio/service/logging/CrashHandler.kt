/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.logging

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures uncaught exceptions to a flat file so they survive process death and can be ingested
 * into the persistent Timber pipeline on the next launch.
 */
object CrashHandler {

  private const val CRASH_FILE = "crash_report.txt"
  private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

  fun install(context: Context) {
    val appContext = context.applicationContext
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      try {
        val timestamp = DATE_FORMAT.format(Date())
        val report = buildString {
          appendLine("=== Crash Report ===")
          appendLine("Time    : $timestamp")
          appendLine("Thread  : ${thread.name} (id=${thread.id})")
          appendLine("Process : ${android.os.Process.myPid()}")
          appendLine()
          append(throwable.stackTraceToString())
        }
        File(appContext.filesDir, CRASH_FILE).writeText(report)
      } catch (_: Throwable) {
        // Writing the report must never prevent the default handler from running.
      }
      defaultHandler?.uncaughtException(thread, throwable)
    }
  }

  fun consumePreviousCrashReport(context: Context): String? {
    val file = File(context.applicationContext.filesDir, CRASH_FILE)
    if (!file.exists()) return null
    try {
      val report = file.readText()
      return report.takeIf { it.isNotBlank() }
    } finally {
      file.delete()
    }
  }
}
