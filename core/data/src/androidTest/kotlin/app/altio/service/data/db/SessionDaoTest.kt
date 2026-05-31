/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionDaoTest {

  private lateinit var db: AiServiceDatabase
  private lateinit var dao: SessionDao

  @Before
  fun setUp() {
    db =
        Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AiServiceDatabase::class.java,
            )
            .allowMainThreadQueries()
            .build()
    dao = db.sessionDao()
  }

  @After
  fun tearDown() {
    db.close()
  }

  // ─── insert / observe ────────────────────────────────────────────────────

  @Test
  fun insertAndObserve_returnsSession() = runTest {
    val entity = makeSession("ses-1", clientId = "c1")
    dao.insert(entity)

    val result = dao.observe("ses-1").first()
    assertNotNull(result)
    assertEquals("ses-1", result?.id)
    assertEquals("c1", result?.clientId)
  }

  @Test
  fun observe_returnsNull_forMissingId() = runTest {
    val result = dao.observe("nonexistent").first()
    assertNull(result)
  }

  @Test
  fun insert_replacesExistingRow() = runTest {
    dao.insert(makeSession("ses-r", clientId = "old"))
    dao.insert(makeSession("ses-r", clientId = "new"))

    val result = dao.observe("ses-r").first()
    assertEquals("new", result?.clientId)
  }

  // ─── delete ───────────────────────────────────────────────────────────────

  @Test
  fun delete_removesSession() = runTest {
    dao.insert(makeSession("ses-del"))
    dao.delete("ses-del")

    val result = dao.observe("ses-del").first()
    assertNull(result)
  }

  // ─── incrementMessageCount ────────────────────────────────────────────────

  @Test
  fun incrementMessageCount_incrementsByOne() = runTest {
    dao.insert(makeSession("ses-inc", messageCount = 2))
    dao.incrementMessageCount("ses-inc", Instant.now())

    val result = dao.observe("ses-inc").first()
    assertEquals(3, result?.messageCount)
  }

  @Test
  fun incrementMessageCount_updatesLastActiveAt() = runTest {
    val original = Instant.parse("2026-01-01T00:00:00Z")
    dao.insert(makeSession("ses-ts", lastActiveAt = original))
    // Truncate to millis to match the precision of the InstantConverter (toEpochMilli)
    val updateTime = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    dao.incrementMessageCount("ses-ts", updateTime)

    val result = dao.observe("ses-ts").first()
    assertTrue(result!!.lastActiveAt >= updateTime)
  }

  // ─── resetSession ─────────────────────────────────────────────────────────

  @Test
  fun resetSession_setsMessageCountToZero() = runTest {
    dao.insert(makeSession("ses-rst", messageCount = 10))
    dao.resetSession("ses-rst", Instant.now())

    val result = dao.observe("ses-rst").first()
    assertEquals(0, result?.messageCount)
  }

  // ─── countByClient ────────────────────────────────────────────────────────

  @Test
  fun countByClient_returnsCorrectCount() = runTest {
    dao.insert(makeSession("ses-a1", clientId = "clientA"))
    dao.insert(makeSession("ses-a2", clientId = "clientA"))
    dao.insert(makeSession("ses-b1", clientId = "clientB"))

    assertEquals(2, dao.countByClient("clientA"))
    assertEquals(1, dao.countByClient("clientB"))
    assertEquals(0, dao.countByClient("clientC"))
  }

  // ─── getStaleSessionIds ───────────────────────────────────────────────────

  @Test
  fun getStaleSessionIds_returnsSessionsOlderThanCutoff() = runTest {
    val now = Instant.now()
    val old = now.minusSeconds(3600) // 1 hour ago — stale
    val recent = now.plusSeconds(3600) // 1 hour in the future — not stale
    val cutoff = now // now is the cutoff boundary

    dao.insert(makeSession("ses-old", lastActiveAt = old))
    dao.insert(makeSession("ses-recent", lastActiveAt = recent))

    val stale = dao.getStaleSessionIds(cutoff)

    assertTrue(stale.contains("ses-old"))
    assertTrue(!stale.contains("ses-recent"))
  }
}

private fun makeSession(
    id: String,
    clientId: String = "client-test",
    messageCount: Int = 0,
    lastActiveAt: Instant = Instant.now(),
) =
    SessionEntity(
        id = id,
        clientId = clientId,
        appName = null,
        modelId = "gemma-3n",
        systemPrompt = null,
        temperature = 1.0f,
        topK = 64,
        topP = 0.95f,
        maxTokens = 2048,
        messageCount = messageCount,
        createdAt = Instant.now(),
        lastActiveAt = lastActiveAt,
    )
