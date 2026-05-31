/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.token

import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.altio.service.data.db.ClientTokenEntity
import app.altio.service.data.db.DurableStateDatabase
import app.altio.service.domain.token.TokenInspectionResult
import java.security.SecureRandom
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TokenRepositoryImplTest {

  private lateinit var db: DurableStateDatabase
  private lateinit var repository: TokenRepositoryImpl

  @Before
  fun setUp() {
    db =
        Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                DurableStateDatabase::class.java,
            )
            .allowMainThreadQueries()
            .build()
    repository = TokenRepositoryImpl(db.clientTokenDao())
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun generateToken_returnsSingleLineToken() = runTest {
    val token = repository.generateToken("demo")

    assertFalse(token.contains("\n"))
    assertFalse(token.contains("\r"))
    assertEquals(43, token.length)
  }

  @Test
  fun validateToken_acceptsLegacyStoredTokenHashWithTrailingNewline() = runTest {
    val rawBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
    val legacyToken = Base64.encodeToString(rawBytes, Base64.URL_SAFE or Base64.NO_PADDING)
    val trimmedToken = legacyToken.trim()

    db.clientTokenDao()
        .insert(
            ClientTokenEntity(
                tokenHash = TokenRepositoryImpl.sha256Hex(legacyToken),
                clientId = "client_legacy",
                label = "Legacy token",
                createdAt = System.currentTimeMillis(),
                lastUsedAt = null,
                isRevoked = false,
            )
        )

    val validated = repository.validateToken(trimmedToken)
    val inspected = repository.inspectToken(trimmedToken)

    assertNotNull(validated)
    assertEquals("client_legacy", validated?.clientId)
    assertTrue(inspected is TokenInspectionResult.Valid)
  }
}
