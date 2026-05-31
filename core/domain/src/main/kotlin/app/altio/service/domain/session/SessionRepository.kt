/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.session

import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
  fun getSession(id: String): Flow<Session?>

  fun observeAllSessions(): Flow<List<Session>>

  fun observeSessionCount(): Flow<Int>

  suspend fun createSession(session: Session): Session

  suspend fun deleteSession(id: String)

  suspend fun deleteAllSessions()

  suspend fun incrementMessageCount(id: String)

  suspend fun touchSession(id: String)

  suspend fun resetSession(id: String)

  suspend fun getStaleSessionIds(cutoff: Instant): List<String>

  /** Returns the total number of sessions belonging to [clientId]. */
  suspend fun countByClient(clientId: String): Int
}
