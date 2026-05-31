/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.logging

import android.util.Log
import app.altio.service.domain.debug.LogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class LogBuffer(
    private val dao: LogEntryDao,
    private val scope: CoroutineScope,
) : Timber.Tree() {
  private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
  val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

  init {
    scope.launch {
      val persisted = dao.getAll().map { it.toDomain() }
      _entries.update { current -> mergeEntries(persisted, current) }
    }
  }

  override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
    val entry = createEntry(priority = priority, tag = tag, message = message, t = t)
    appendEntry(entry)
    scope.launch {
      dao.insert(entry.toEntity())
      dao.trimToLimit(MAX_ENTRIES)
    }
  }

  fun persistRecoveredCrash(report: String) {
    val entry =
        createEntry(
            priority = Log.ERROR,
            tag = "CrashHandler",
            message = "Previous crash recovered:\n$report",
        )
    appendEntry(entry)
    runBlocking {
      dao.insert(entry.toEntity())
      dao.trimToLimit(MAX_ENTRIES)
    }
  }

  fun clear() {
    _entries.value = emptyList()
    scope.launch { dao.clear() }
  }

  private fun appendEntry(entry: LogEntry) {
    _entries.update { current -> trimToLimit(current + entry) }
  }

  private fun createEntry(priority: Int, tag: String?, message: String, t: Throwable? = null) =
      LogEntry(
          timestamp = System.currentTimeMillis(),
          priority = priority,
          tag = tag,
          message = if (t != null) "$message\n${t.stackTraceToString()}" else message,
      )

  companion object {
    const val MAX_ENTRIES = 2000
  }
}

private fun mergeEntries(persisted: List<LogEntry>, current: List<LogEntry>): List<LogEntry> =
    trimToLimit((persisted + current).distinctBy { entryKey(it) })

private fun trimToLimit(entries: List<LogEntry>): List<LogEntry> =
    if (entries.size > LogBuffer.MAX_ENTRIES) entries.takeLast(LogBuffer.MAX_ENTRIES) else entries

private fun entryKey(entry: LogEntry): String =
    "${entry.timestamp}|${entry.priority}|${entry.tag}|${entry.message}"

private fun LogEntry.toEntity() =
    LogEntryEntity(
        timestamp = timestamp,
        priority = priority,
        tag = tag,
        message = message,
    )

private fun LogEntryEntity.toDomain() =
    LogEntry(
        timestamp = timestamp,
        priority = priority,
        tag = tag,
        message = message,
    )
