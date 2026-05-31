/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.session

import app.altio.service.data.db.SessionDao
import app.altio.service.data.db.SessionEntity
import app.altio.service.domain.runtime.GenerationConfig
import app.altio.service.domain.session.Session
import app.altio.service.domain.session.SessionRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionRepositoryImpl(private val dao: SessionDao) : SessionRepository {

  override fun getSession(id: String): Flow<Session?> = dao.observe(id).map { it?.toDomain() }

  override fun observeAllSessions(): Flow<List<Session>> =
      dao.observeAll().map { sessions -> sessions.map { it.toDomain() } }

  override fun observeSessionCount(): Flow<Int> = dao.observeCount()

  override suspend fun createSession(session: Session): Session {
    dao.insert(session.toEntity())
    return session
  }

  override suspend fun deleteSession(id: String) {
    dao.delete(id)
  }

  override suspend fun deleteAllSessions() {
    dao.deleteAll()
  }

  override suspend fun incrementMessageCount(id: String) {
    dao.incrementMessageCount(id, Instant.now())
  }

  override suspend fun touchSession(id: String) {
    dao.touchSession(id, Instant.now())
  }

  override suspend fun resetSession(id: String) {
    dao.resetSession(id, Instant.now())
  }

  override suspend fun getStaleSessionIds(cutoff: Instant): List<String> =
      dao.getStaleSessionIds(cutoff)

  override suspend fun countByClient(clientId: String): Int = dao.countByClient(clientId)

  private fun SessionEntity.toDomain() =
      Session(
          id = id,
          clientId = clientId,
          appName = appName,
          modelId = modelId,
          systemPrompt = systemPrompt,
          generationConfig =
              GenerationConfig(
                  temperature = temperature,
                  topK = topK,
                  topP = topP,
                  maxTokens = maxTokens,
              ),
          messageCount = messageCount,
          createdAt = createdAt,
          lastActiveAt = lastActiveAt,
      )

  private fun Session.toEntity() =
      SessionEntity(
          id = id,
          clientId = clientId,
          appName = appName,
          modelId = modelId,
          systemPrompt = systemPrompt,
          temperature = generationConfig.temperature,
          topK = generationConfig.topK,
          topP = generationConfig.topP,
          maxTokens = generationConfig.maxTokens,
          messageCount = messageCount,
          createdAt = createdAt,
          lastActiveAt = lastActiveAt,
      )
}
