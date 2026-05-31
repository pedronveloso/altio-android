/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.runtime

import app.altio.service.data.session.SessionCoordinator
import app.altio.service.domain.session.SessionRepository
import app.altio.service.domain.settings.SettingsRepository
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Periodically closes sessions that have been inactive longer than the configured idle timeout.
 * When [SettingsRepository.settings] `idleShutdownMinutes` is `null`, idle eviction is disabled.
 */
class IdleSessionCleaner(
    private val sessionRepository: SessionRepository,
    private val sessionCoordinator: SessionCoordinator,
    private val settingsRepository: SettingsRepository,
    scope: CoroutineScope,
) {
  init {
    scope.launch {
      while (true) {
        delay(CHECK_INTERVAL_MS)
        val idleMinutes =
            settingsRepository.settings.first().idleShutdownMinutes
                ?: continue // Never mode — skip eviction
        val cutoff = Instant.now().minusSeconds(idleMinutes * 60L)
        sessionRepository.getStaleSessionIds(cutoff).forEach { id ->
          Timber.i("Evicting idle session %s (idle > %d min)", id, idleMinutes)
          sessionCoordinator.deleteSession(id)
        }
      }
    }
  }

  companion object {
    private const val CHECK_INTERVAL_MS = 60_000L
  }
}
