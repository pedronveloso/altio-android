/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.runtime

import app.altio.service.domain.model.Model
import app.altio.service.domain.runtime.RuntimeConfig
import app.altio.service.domain.runtime.RuntimeSession
import app.altio.service.domain.runtime.SessionParams
import app.altio.service.domain.session.Session
import app.altio.service.domain.session.SessionRepository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import timber.log.Timber

/** Manages active [RuntimeSession]s keyed by session ID. Thread-safe. */
open class RuntimeSessionManager(
    private val engineHolder: RuntimeEngineHolder,
    private val sessionRepository: SessionRepository,
) {
  private val sessions = ConcurrentHashMap<String, RuntimeSession>()
  private val _activeSessionCount = MutableStateFlow(0)

  val activeSessionCount: StateFlow<Int> = _activeSessionCount.asStateFlow()

  init {
    engineHolder.registerOnEngineWillReset { invalidateAllSessions() }
  }

  constructor(engineHolder: RuntimeEngineHolder) : this(engineHolder, NoOpSessionRepository)

  suspend fun openSession(
      sessionId: String,
      model: Model,
      config: RuntimeConfig,
      params: SessionParams,
  ): RuntimeSession {
    Timber.d("Opening session for model %s", model.definition.id)
    val engine = engineHolder.load(model, config)
    val session = engine.createSession(sessionId, params)
    sessions[sessionId] = session
    _activeSessionCount.value = sessions.size
    return session
  }

  open fun getSession(sessionId: String): RuntimeSession? = sessions[sessionId]

  fun closeSession(sessionId: String) {
    Timber.d("Closing session %s", sessionId)
    sessions.remove(sessionId)?.close()
    _activeSessionCount.value = sessions.size
  }

  fun closeAll() {
    Timber.d("Closing all %d sessions", sessions.size)
    sessions.values.forEach { it.close() }
    sessions.clear()
    _activeSessionCount.value = 0
  }

  suspend fun invalidateAllSessions() {
    if (sessions.isNotEmpty()) {
      Timber.i("Invalidating %d runtime sessions", sessions.size)
    }
    closeAll()
    sessionRepository.deleteAllSessions()
  }

  private companion object {
    val NoOpSessionRepository: SessionRepository =
        object : SessionRepository {
          override fun getSession(id: String): Flow<Session?> = flowOf(null)

          override fun observeAllSessions(): Flow<List<Session>> = flowOf(emptyList())

          override fun observeSessionCount(): Flow<Int> = flowOf(0)

          override suspend fun createSession(session: Session): Session = session

          override suspend fun deleteSession(id: String) {}

          override suspend fun deleteAllSessions() {}

          override suspend fun incrementMessageCount(id: String) {}

          override suspend fun touchSession(id: String) {}

          override suspend fun resetSession(id: String) {}

          override suspend fun getStaleSessionIds(cutoff: Instant): List<String> = emptyList()

          override suspend fun countByClient(clientId: String): Int = 0
        }
  }
}
