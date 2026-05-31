/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.altio.service.state.serviceConfigurationFailure
import app.altio.service.state.serviceLaunchBlockedFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

    val pending = goAsync()
    val appGraph = (context.applicationContext as AiServiceApplication).appGraph

    CoroutineScope(Dispatchers.IO).launch {
      try {
        val startOnBoot =
            runCatching { appGraph.settingsRepository.settings.first().startOnBoot }
                .getOrElse { error ->
                  val failure = serviceConfigurationFailure(error)
                  appGraph.serviceStartFailureStore.setFailure(failure)
                  Timber.e(error, "%s %s: %s", failure.code, failure.title, failure.technicalDetail)
                  return@launch
                }
        Timber.i("Boot received — startOnBoot=%b", startOnBoot)
        if (startOnBoot) {
          Timber.i("Launching AiBackgroundService on boot")
          runCatching {
                context.startForegroundService(Intent(context, AiBackgroundService::class.java))
              }
              .onFailure { error ->
                val failure = serviceLaunchBlockedFailure(error)
                appGraph.serviceStartFailureStore.setFailure(failure)
                Timber.e(error, "%s %s: %s", failure.code, failure.title, failure.technicalDetail)
              }
        }
      } catch (t: Throwable) {
        Timber.e(t, "Failed handling boot completed")
      } finally {
        pending.finish()
      }
    }
  }
}
