/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.session

import app.altio.service.data.runtime.RuntimeSessionManager
import app.altio.service.domain.model.Model
import app.altio.service.domain.runtime.RuntimeConfig
import app.altio.service.domain.runtime.SessionParams
import app.altio.service.domain.session.Session
import app.altio.service.domain.session.SessionRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates session mutations across the runtime layer and the persisted repository so callers do
 * not need to orchestrate both concerns manually.
 */
class SessionCoordinator(
    private val sessionRepository: SessionRepository,
    private val sessionManager: RuntimeSessionManager,
) {
  private val createSessionMutex = Mutex()

  suspend fun createSession(
      session: Session,
      model: Model,
      config: RuntimeConfig,
      params: SessionParams,
  ): Session =
      createSessionMutex.withLock {
        if (sessionManager.activeSessionCount.value > 0) {
          throw ActiveRuntimeSessionException()
        }
        sessionManager.openSession(
            sessionId = session.id,
            model = model,
            config = config,
            params = params,
        )
        return@withLock runCatching { sessionRepository.createSession(session) }
            .onFailure { sessionManager.closeSession(session.id) }
            .getOrThrow()
      }

  suspend fun deleteSession(id: String) {
    sessionManager.closeSession(id)
    sessionRepository.deleteSession(id)
  }

  suspend fun deleteAllSessions() {
    sessionManager.closeAll()
    sessionRepository.deleteAllSessions()
  }

  suspend fun resetSession(id: String) {
    sessionManager.getSession(id)?.reset()
    sessionRepository.resetSession(id)
  }

  suspend fun touchSession(id: String) {
    sessionRepository.touchSession(id)
  }

  suspend fun incrementMessageCount(id: String) {
    sessionRepository.incrementMessageCount(id)
  }

  suspend fun invalidateAllSessions() {
    sessionManager.invalidateAllSessions()
  }
}

class ActiveRuntimeSessionException :
    IllegalStateException("LiteRT currently supports one active runtime session at a time.")
