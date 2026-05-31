/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.altio.service.domain.settings.AppSettings
import app.altio.service.domain.settings.OnboardingCheckpoint
import app.altio.service.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

  override val settings: Flow<AppSettings> =
      dataStore.data.map { prefs ->
        AppSettings(
            setupComplete = prefs[SETUP_COMPLETE] ?: false,
            onboardingCheckpoint =
                prefs[ONBOARDING_CHECKPOINT]?.let {
                  runCatching { OnboardingCheckpoint.valueOf(it) }.getOrNull()
                } ?: OnboardingCheckpoint.WELCOME,
            batteryOptimizationGuidanceSeen = prefs[BATTERY_OPTIMIZATION_GUIDANCE_SEEN] ?: false,
            oemGuidanceSeen = prefs[OEM_GUIDANCE_SEEN] ?: false,
            activeModelId = prefs[ACTIVE_MODEL_ID] ?: "demo-model",
            idleShutdownMinutes = prefs[IDLE_SHUTDOWN_MINUTES],
            startOnBoot = prefs[START_ON_BOOT] ?: false,
            serverPort = prefs[SERVER_PORT] ?: AppSettings.DEFAULT_SERVER_PORT,
            accelerator = prefs[ACCELERATOR] ?: "Auto",
            modelAccelerators = decodeModelAccelerators(prefs[MODEL_ACCELERATORS]),
            maxTokens = prefs[MAX_TOKENS] ?: 2048,
        )
      }

  override suspend fun setSetupComplete(complete: Boolean) {
    dataStore.edit { it[SETUP_COMPLETE] = complete }
  }

  override suspend fun setOnboardingCheckpoint(checkpoint: OnboardingCheckpoint) {
    dataStore.edit { it[ONBOARDING_CHECKPOINT] = checkpoint.name }
  }

  override suspend fun setBatteryOptimizationGuidanceSeen(seen: Boolean) {
    dataStore.edit { it[BATTERY_OPTIMIZATION_GUIDANCE_SEEN] = seen }
  }

  override suspend fun setOemGuidanceSeen(seen: Boolean) {
    dataStore.edit { it[OEM_GUIDANCE_SEEN] = seen }
  }

  override suspend fun setActiveModelId(modelId: String) {
    val previousModelId = settings.first().activeModelId
    dataStore.edit { it[ACTIVE_MODEL_ID] = modelId }
    if (previousModelId == modelId) {
      Timber.d("Active model unchanged: %s", modelId)
    } else {
      Timber.i("Active model changed from %s to %s", previousModelId, modelId)
    }
  }

  override suspend fun setIdleShutdownMinutes(minutes: Int?) {
    dataStore.edit { prefs ->
      if (minutes == null) prefs.remove(IDLE_SHUTDOWN_MINUTES)
      else prefs[IDLE_SHUTDOWN_MINUTES] = minutes
    }
  }

  override suspend fun setStartOnBoot(enabled: Boolean) {
    dataStore.edit { it[START_ON_BOOT] = enabled }
  }

  override suspend fun setServerPort(port: Int) {
    require(port in AppSettings.MIN_SERVER_PORT..AppSettings.MAX_SERVER_PORT) {
      "Server port must be between ${AppSettings.MIN_SERVER_PORT} and ${AppSettings.MAX_SERVER_PORT}."
    }
    dataStore.edit { it[SERVER_PORT] = port }
  }

  override suspend fun setAccelerator(accelerator: String) {
    dataStore.edit { it[ACCELERATOR] = accelerator }
  }

  override suspend fun setModelAccelerator(modelId: String, accelerator: String?) {
    dataStore.edit { prefs ->
      val updated =
          decodeModelAccelerators(prefs[MODEL_ACCELERATORS]).toMutableMap().apply {
            if (accelerator == null) remove(modelId) else put(modelId, accelerator)
          }
      if (updated.isEmpty()) {
        prefs.remove(MODEL_ACCELERATORS)
      } else {
        prefs[MODEL_ACCELERATORS] = encodeModelAccelerators(updated)
      }
    }
  }

  override suspend fun setMaxTokens(tokens: Int) {
    dataStore.edit { it[MAX_TOKENS] = tokens }
  }

  private fun decodeModelAccelerators(encoded: String?): Map<String, String> =
      encoded
          ?.split(',')
          ?.mapNotNull { entry ->
            val separator = entry.indexOf(':')
            if (separator <= 0 || separator == entry.lastIndex) return@mapNotNull null
            val modelId = entry.substring(0, separator)
            val accelerator = entry.substring(separator + 1)
            if (modelId.isBlank() || accelerator.isBlank()) null else modelId to accelerator
          }
          ?.toMap()
          .orEmpty()

  private fun encodeModelAccelerators(modelAccelerators: Map<String, String>): String =
      modelAccelerators.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

  companion object {
    private val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
    private val ONBOARDING_CHECKPOINT = stringPreferencesKey("onboarding_checkpoint")
    private val BATTERY_OPTIMIZATION_GUIDANCE_SEEN =
        booleanPreferencesKey("battery_optimization_guidance_seen")
    private val OEM_GUIDANCE_SEEN = booleanPreferencesKey("oem_guidance_seen")
    private val ACTIVE_MODEL_ID = stringPreferencesKey("active_model_id")
    private val IDLE_SHUTDOWN_MINUTES = intPreferencesKey("idle_shutdown_minutes")
    private val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
    private val SERVER_PORT = intPreferencesKey("server_port")
    private val ACCELERATOR = stringPreferencesKey("accelerator")
    private val MODEL_ACCELERATORS = stringPreferencesKey("model_accelerators")
    private val MAX_TOKENS = intPreferencesKey("max_tokens")
  }
}
