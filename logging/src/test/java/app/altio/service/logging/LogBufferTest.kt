/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.logging

import android.util.Log
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogBufferTest {

  private val dispatcher = UnconfinedTestDispatcher()
  private val scope = CoroutineScope(dispatcher)

  private fun dao(stored: List<LogEntryEntity> = emptyList()): LogEntryDao =
      mockk<LogEntryDao>().also {
        coEvery { it.getAll() } returns stored
        coJustRun { it.insert(any()) }
        coJustRun { it.trimToLimit(any()) }
        coJustRun { it.clear() }
      }

  @Test
  fun `log entries are added to StateFlow`() =
      runTest(dispatcher) {
        val buffer = LogBuffer(dao(), scope)

        buffer.d("hello")

        assertEquals(1, buffer.entries.value.size)
        assertEquals("hello", buffer.entries.value.first().message)
      }

  @Test
  fun `in-memory entries are capped at MAX_ENTRIES`() =
      runTest(dispatcher) {
        val buffer = LogBuffer(dao(), scope)

        repeat(LogBuffer.MAX_ENTRIES + 1) { i -> buffer.d("message $i") }

        assertEquals(LogBuffer.MAX_ENTRIES, buffer.entries.value.size)
        assertEquals("message ${LogBuffer.MAX_ENTRIES}", buffer.entries.value.last().message)
      }

  @Test
  fun `each log call persists entry to dao`() =
      runTest(dispatcher) {
        val mockDao = dao()
        val buffer = LogBuffer(mockDao, scope)

        buffer.i("persisted entry")

        coVerify(exactly = 1) { mockDao.insert(any()) }
      }

  @Test
  fun `each log call trims dao to MAX_ENTRIES`() =
      runTest(dispatcher) {
        val mockDao = dao()
        val buffer = LogBuffer(mockDao, scope)

        buffer.w("some warning")

        coVerify(exactly = 1) { mockDao.trimToLimit(LogBuffer.MAX_ENTRIES) }
      }

  @Test
  fun `recovered crash is persisted immediately`() =
      runTest(dispatcher) {
        val mockDao = dao()
        val buffer = LogBuffer(mockDao, scope)

        buffer.persistRecoveredCrash("boom")

        val entry = buffer.entries.value.single()
        assertEquals(6, entry.priority)
        assertEquals("CrashHandler", entry.tag)
        assertTrue(entry.message.contains("Previous crash recovered:"))
        assertTrue(entry.message.contains("boom"))
        coVerify(exactly = 1) { mockDao.insert(any()) }
        coVerify(exactly = 1) { mockDao.trimToLimit(LogBuffer.MAX_ENTRIES) }
      }

  @Test
  fun `persisted entries are loaded on init`() =
      runTest(dispatcher) {
        val stored =
            listOf(
                LogEntryEntity(
                    id = 1,
                    timestamp = 1000L,
                    priority = Log.DEBUG,
                    tag = null,
                    message = "old 1",
                ),
                LogEntryEntity(
                    id = 2,
                    timestamp = 2000L,
                    priority = Log.INFO,
                    tag = "TAG",
                    message = "old 2",
                ),
            )
        val buffer = LogBuffer(dao(stored), scope)

        assertEquals(2, buffer.entries.value.size)
        assertEquals("old 1", buffer.entries.value[0].message)
        assertEquals("old 2", buffer.entries.value[1].message)
      }

  @Test
  fun `log entry priority is preserved`() =
      runTest(dispatcher) {
        val buffer = LogBuffer(dao(), scope)

        buffer.log(Log.ERROR, "test error")

        assertEquals(Log.ERROR, buffer.entries.value.first().priority)
      }

  @Test
  fun `log with throwable appends stack trace to message`() =
      runTest(dispatcher) {
        val buffer = LogBuffer(dao(), scope)

        val ex = RuntimeException("boom")
        buffer.e(ex, "failed")

        val message = buffer.entries.value.first().message
        assertTrue(message.startsWith("failed"))
        assertTrue(message.contains("RuntimeException"))
      }

  @Test
  fun `init load does not overwrite startup recovered crash entry`() =
      runTest(dispatcher) {
        val loadGate = CompletableDeferred<Unit>()
        val mockDao =
            mockk<LogEntryDao>().also {
              coEvery { it.getAll() } coAnswers
                  {
                    loadGate.await()
                    emptyList()
                  }
              coJustRun { it.insert(any()) }
              coJustRun { it.trimToLimit(any()) }
              coJustRun { it.clear() }
            }

        val bufferDeferred = async { LogBuffer(mockDao, scope) }
        val buffer = bufferDeferred.await()

        buffer.persistRecoveredCrash("boom")
        loadGate.complete(Unit)

        val messages = buffer.entries.value.map { it.message }
        assertEquals(1, messages.size)
        assertTrue(messages.single().contains("boom"))
        assertFalse(messages.any { it.isBlank() })
      }
}
