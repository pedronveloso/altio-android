/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
  val settings: Flow<AppSettings>

  suspend fun setSetupComplete(complete: Boolean)

  suspend fun setOnboardingCheckpoint(checkpoint: OnboardingCheckpoint)

  suspend fun setBatteryOptimizationGuidanceSeen(seen: Boolean)

  suspend fun setOemGuidanceSeen(seen: Boolean)

  suspend fun setActiveModelId(modelId: String)

  suspend fun setIdleShutdownMinutes(minutes: Int?)

  suspend fun setStartOnBoot(enabled: Boolean)

  suspend fun setServerPort(port: Int)

  suspend fun setAccelerator(accelerator: String)

  suspend fun setModelAccelerator(modelId: String, accelerator: String?)

  suspend fun setMaxTokens(tokens: Int)
}
