/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.token

import android.util.Base64
import app.altio.service.data.db.ClientTokenDao
import app.altio.service.data.db.ClientTokenEntity
import app.altio.service.domain.token.ClientToken
import app.altio.service.domain.token.TokenInspectionResult
import app.altio.service.domain.token.TokenRepository
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TokenRepositoryImpl(private val dao: ClientTokenDao) : TokenRepository {

  override suspend fun generateToken(label: String): String {
    val rawBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
    val rawToken =
        Base64.encodeToString(rawBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    val hash = sha256Hex(rawToken)
    val entity =
        ClientTokenEntity(
            tokenHash = hash,
            clientId = "client_${UUID.randomUUID()}",
            label = label,
            createdAt = System.currentTimeMillis(),
            lastUsedAt = null,
            isRevoked = false,
        )
    dao.insert(entity)
    return rawToken
  }

  override suspend fun validateToken(rawToken: String): ClientToken? {
    val match = findMatchingToken(rawToken) ?: return null
    val (hash, entity) = match
    if (entity.isRevoked) return null
    dao.updateLastUsed(hash, System.currentTimeMillis())
    return entity.toDomain()
  }

  override suspend fun inspectToken(rawToken: String): TokenInspectionResult {
    val (_, entity) = findMatchingToken(rawToken) ?: return TokenInspectionResult.Unknown
    return if (entity.isRevoked) {
      TokenInspectionResult.Revoked(entity.toDomain())
    } else {
      TokenInspectionResult.Valid(entity.toDomain())
    }
  }

  override suspend fun revokeToken(tokenHash: String) {
    dao.revoke(tokenHash)
  }

  override fun observeTokens(): Flow<List<ClientToken>> =
      dao.observeActive().map { list -> list.map { it.toDomain() } }

  private fun ClientTokenEntity.toDomain() =
      ClientToken(
          tokenHash = tokenHash,
          clientId = clientId,
          label = label,
          createdAt = createdAt,
          lastUsedAt = lastUsedAt,
          isRevoked = isRevoked,
      )

  private suspend fun findMatchingToken(rawToken: String): Pair<String, ClientTokenEntity>? {
    for (candidate in tokenCandidates(rawToken)) {
      val hash = sha256Hex(candidate)
      val entity = dao.findByHash(hash)
      if (entity != null) return hash to entity
    }
    return null
  }

  companion object {
    fun sha256Hex(input: String): String {
      val digest = MessageDigest.getInstance("SHA-256")
      return digest.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun tokenCandidates(rawToken: String): List<String> {
      val trimmed = rawToken.trim()
      return buildList {
            add(rawToken)
            if (trimmed != rawToken) add(trimmed)
            add(trimmed + "\n")
            add(trimmed + "\r\n")
          }
          .distinct()
    }
  }
}
