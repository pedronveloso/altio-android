/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.altio.sdk.android.discovery.AltioServiceContract
import app.altio.service.data.notification.AppNotificationChannels
import app.altio.service.state.ServiceStartFailure
import app.altio.service.state.classifyServerStartFailure
import app.altio.service.state.serviceConfigurationFailure
import app.altio.service.state.serviceNotificationFailure
import app.altio.service.state.unexpectedServiceStartFailure
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class AiBackgroundService : Service() {

  private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    Timber.tag("AiBackgroundService").e(throwable, "Unhandled coroutine exception")
  }
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + coroutineExceptionHandler)
  private lateinit var appGraph: AppGraph
  private val copyPortReceiver = CopyPortReceiver()
  private var copyPortReceiverRegistered = false

  override fun onCreate() {
    super.onCreate()
    Timber.i("AiBackgroundService created")
    appGraph = (application as AiServiceApplication).appGraph
    appGraph.idleSessionCleaner
    runCatching {
          AppNotificationChannels.ensureServiceStatus(this)
          startForeground(
              AppNotificationChannels.SERVICE_STATUS_NOTIFICATION_ID,
              buildNotification("Starting…"),
          )
        }
        .onFailure { error ->
          recordStartFailure(serviceNotificationFailure(error), error)
          stopSelf()
          return
        }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      registerReceiver(
          copyPortReceiver,
          IntentFilter(CopyPortReceiver.ACTION_COPY_PORT),
          RECEIVER_NOT_EXPORTED,
      )
    } else {
      @Suppress("UnspecifiedRegisterReceiverFlag")
      registerReceiver(copyPortReceiver, IntentFilter(CopyPortReceiver.ACTION_COPY_PORT))
    }
    copyPortReceiverRegistered = true

    combine(
            appGraph.server.port,
            appGraph.sessionManager.activeSessionCount,
            appGraph.serviceStartFailureStore.failure,
        ) { port, activeSessions, failure ->
          Triple(port, activeSessions, failure)
        }
        .onEach { (port, activeSessions, failure) ->
          Timber.i("Server port → %s", port)
          updateNotification(
              status = notificationStatus(port, activeSessions, failure),
              port = port,
          )

          // Broadcast port change so clients can discover the new port
          sendBroadcast(
              Intent(ACTION_PORT_CHANGED).apply {
                setPackage(packageName)
                port?.let { putExtra(EXTRA_PORT, it) }
              }
          )
        }
        .launchIn(scope)

    appGraph.settingsRepository.settings
        .map { it.serverPort }
        .distinctUntilChanged()
        .retryWhen { error, _ ->
          recordStartFailure(serviceConfigurationFailure(error), error)
          delay(SETTINGS_RETRY_DELAY_MS)
          true
        }
        .onEach { configuredPort -> applyConfiguredPort(configuredPort) }
        .launchIn(scope)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START_OR_RESTART,
      null -> retryServerStart()
    }
    return START_STICKY
  }

  /**
   * Responds to system memory pressure. On critical trim levels the model is evicted from memory —
   * this releases several GB of RAM immediately. The HTTP server keeps running; subsequent
   * inference calls will return 503 until a new session (re-)loads the model.
   */
  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
      Timber.w("Memory pressure level %d — evicting model", level)
      scope.launch(Dispatchers.IO) { appGraph.engineHolder.unload() }
    }
  }

  override fun onDestroy() {
    Timber.i("AiBackgroundService destroyed")
    if (copyPortReceiverRegistered) {
      unregisterReceiver(copyPortReceiver)
    }
    runBlocking(Dispatchers.IO) {
      runCatching { appGraph.sessionCoordinator.invalidateAllSessions() }
          .onFailure { Timber.e(it, "Failed clearing sessions on service destroy") }
    }
    appGraph.server.stop()
    scope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun retryServerStart() {
    scope.launch {
      val configuredPort =
          runCatching { appGraph.settingsRepository.settings.first().serverPort }
              .getOrElse { error ->
                recordStartFailure(serviceConfigurationFailure(error), error)
                return@launch
              }
      if (appGraph.server.port.value == configuredPort) {
        Timber.d("Server already running on configured port %d", configuredPort)
        return@launch
      }
      applyConfiguredPort(configuredPort)
    }
  }

  private fun applyConfiguredPort(configuredPort: Int) {
    val currentPort = appGraph.server.port.value
    if (currentPort == configuredPort) {
      Timber.d("Configured port unchanged: %d", configuredPort)
      return
    }
    if (currentPort != null) {
      Timber.i("Restarting HTTP server from port %d to %d", currentPort, configuredPort)
      runCatching { appGraph.server.preflightStart(configuredPort) }
          .onFailure { error ->
            recordStartFailure(classifyServerStartFailure(error, configuredPort), error)
            updateNotification(
                status =
                    notificationStatus(
                        port = currentPort,
                        activeSessions = appGraph.sessionManager.activeSessionCount.value,
                        startFailure = appGraph.serviceStartFailureStore.failure.value,
                    ),
                port = currentPort,
            )
            return
          }
      appGraph.server.stop()
    }
    startServer(configuredPort)
  }

  private fun startServer(configuredPort: Int) {
    appGraph.serviceStartFailureStore.clear()
    runCatching { appGraph.server.start(configuredPort) }
        .onSuccess { appGraph.serviceStartFailureStore.clear() }
        .onFailure { error ->
          val failure =
              if (error is Exception) {
                classifyServerStartFailure(error, configuredPort)
              } else {
                unexpectedServiceStartFailure(error, configuredPort)
              }
          recordStartFailure(failure, error)
          updateNotification(
              status = notificationStatus(null, 0, failure),
              port = null,
          )
        }
  }

  private fun recordStartFailure(failure: ServiceStartFailure, throwable: Throwable) {
    appGraph.serviceStartFailureStore.setFailure(failure)
    Timber.e(throwable, "%s %s: %s", failure.code, failure.title, failure.technicalDetail)
  }

  private fun updateNotification(status: String, port: Int?) {
    val nm = getSystemService(android.app.NotificationManager::class.java)
    nm.notify(
        AppNotificationChannels.SERVICE_STATUS_NOTIFICATION_ID,
        buildNotification(status, port),
    )
  }

  private fun buildNotification(status: String, port: Int? = null): Notification {
    val openIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
    val builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Altio Service")
            .setContentText(status)
            .setStyle(NotificationCompat.BigTextStyle().bigText(status))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openIntent)
            .setOngoing(true)

    if (port != null) {
      val copyIntent =
          PendingIntent.getBroadcast(
              this,
              0,
              Intent(CopyPortReceiver.ACTION_COPY_PORT)
                  .setPackage(packageName)
                  .putExtra(CopyPortReceiver.EXTRA_PORT, port),
              PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
          )
      builder.addAction(android.R.drawable.ic_menu_share, "Copy Port", copyIntent)
    }

    return builder.build()
  }

  companion object {
    private const val CHANNEL_ID = AppNotificationChannels.SERVICE_STATUS_CHANNEL_ID
    private const val SETTINGS_RETRY_DELAY_MS = 5_000L
    const val ACTION_START_OR_RESTART = "app.altio.service.action.START_OR_RESTART"
    const val ACTION_PORT_CHANGED = AltioServiceContract.actionPortChanged
    const val EXTRA_PORT = AltioServiceContract.extraPort
  }
}

internal fun notificationStatus(
    port: Int?,
    activeSessions: Int,
    startFailure: ServiceStartFailure? = null,
): String =
    if (startFailure != null) {
      "${startFailure.code}: ${startFailure.title}"
    } else if (port == null) {
      "Stopped"
    } else {
      buildString {
        append("Running on port ")
        append(port)
        if (activeSessions > 0) {
          append(" • ")
          append(activeSessions)
          append(if (activeSessions == 1) " active session" else " active sessions")
        }
      }
    }
