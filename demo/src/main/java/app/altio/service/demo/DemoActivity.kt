/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.demo

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import app.altio.sdk.client.AltioAiServiceClient
import app.altio.sdk.client.HttpFailureException
import app.altio.sdk.client.RateLimitedException
import app.altio.sdk.client.ServiceUnavailableException
import app.altio.sdk.client.UnauthorizedException
import app.altio.sdk.contract.session.SessionResponse
import app.altio.service.domain.debug.LogEntry
import app.altio.service.logging.ui.DebugLogEntries
import app.altio.service.logging.ui.DebugLogsScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val PREFS_NAME = "demo_config"
private const val KEY_LAST_SUCCESS_PORT = "last_success_port"
private const val KEY_LAST_SUCCESS_TOKEN = "last_success_token"
private const val KEY_DRAFT_PORT = "draft_port"
private const val KEY_DRAFT_TOKEN = "draft_token"
private const val KEY_SESSION_ID = "session_id"

@Immutable private data class DemoLogEntries(val items: List<LogEntry>)

/**
 * Demo launcher for the Altio Service. Shows a config screen on first launch to collect the server
 * port and bearer token. After connecting, creates a session automatically and shows a tabbed
 * interface covering Chat, Audio, Jobs, Health, and HTTP Log.
 *
 * To connect: run the main app on the same device, generate a bearer token in Settings → Manage API
 * Tokens, then note the port shown on the Dashboard.
 */
class DemoActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
    )
    super.onCreate(savedInstanceState)

    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    setContent {
      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          DemoApp(
              lastSuccessfulPort = prefs.getString(KEY_LAST_SUCCESS_PORT, "") ?: "",
              lastSuccessfulToken = prefs.getString(KEY_LAST_SUCCESS_TOKEN, "") ?: "",
              draftPort = readDraftPort(prefs),
              draftToken = prefs.getString(KEY_DRAFT_TOKEN, "") ?: "",
              savedSessionId = prefs.getString(KEY_SESSION_ID, "") ?: "",
              onSaveDraftConfig = { port, token ->
                prefs.edit {
                  putString(KEY_DRAFT_PORT, port)
                  putString(KEY_DRAFT_TOKEN, token)
                }
              },
              onSaveSuccessfulConfig = { port, token ->
                prefs.edit {
                  putString(KEY_LAST_SUCCESS_PORT, port)
                  putString(KEY_LAST_SUCCESS_TOKEN, token)
                  putString(KEY_DRAFT_PORT, port)
                  putString(KEY_DRAFT_TOKEN, token)
                }
              },
              onSaveSessionId = { id -> prefs.edit { putString(KEY_SESSION_ID, id) } },
              onClearConfig = {
                prefs.edit {
                  remove(KEY_LAST_SUCCESS_PORT)
                  remove(KEY_LAST_SUCCESS_TOKEN)
                  remove(KEY_DRAFT_PORT)
                  remove(KEY_DRAFT_TOKEN)
                  remove(KEY_SESSION_ID)
                }
              },
          )
        }
      }
    }
  }
}

@Composable
private fun DemoApp(
    lastSuccessfulPort: String,
    lastSuccessfulToken: String,
    draftPort: String,
    draftToken: String,
    savedSessionId: String,
    onSaveDraftConfig: (String, String) -> Unit,
    onSaveSuccessfulConfig: (String, String) -> Unit,
    onSaveSessionId: (String) -> Unit,
    onClearConfig: () -> Unit,
) {
  val application = LocalContext.current.applicationContext as DemoApplication
  val logEntries by application.logging.entries.collectAsState()
  val trackedJobIds = remember { mutableStateListOf<String>() }
  var logSearchQuery by remember { mutableStateOf("") }

  var currentDraftPort by rememberSaveable {
    mutableStateOf(draftPort.ifBlank { lastSuccessfulPort })
  }
  var currentDraftToken by rememberSaveable {
    mutableStateOf(draftToken.ifBlank { lastSuccessfulToken })
  }
  var activePort by rememberSaveable { mutableStateOf("") }
  var activeToken by rememberSaveable { mutableStateOf("") }
  var sessionId by rememberSaveable { mutableStateOf(savedSessionId) }
  var phase by remember { mutableStateOf<StartupPhase>(StartupPhase.Initializing) }
  var recoveryReason by remember { mutableStateOf<String?>(null) }

  // Build client once port + token are known
  val client =
      remember(activePort, activeToken) {
        val p = activePort.toIntOrNull() ?: return@remember null
        if (activeToken.isBlank()) return@remember null
        AltioAiServiceClient(
            port = p,
            bearerToken = activeToken,
            logSink = { Timber.tag("Http").d(it) },
        )
      }

  LaunchedEffect(Unit) {
    val hasSuccessfulConfig = lastSuccessfulPort.isNotBlank() && lastSuccessfulToken.isNotBlank()
    if (hasSuccessfulConfig) {
      activePort = lastSuccessfulPort
      activeToken = lastSuccessfulToken
      phase = StartupPhase.Reconnecting
    } else {
      phase = StartupPhase.ConfigRequired
    }
  }

  suspend fun establishConnection(port: String, token: String, persistSuccess: Boolean): String? {
    val normalizedPort = port.toIntOrNull()?.toString() ?: return "Invalid port."
    val normalizedToken = token.trim()
    activePort = normalizedPort
    activeToken = normalizedToken
    val connectionClient =
        AltioAiServiceClient(
            port = normalizedPort.toInt(),
            bearerToken = normalizedToken,
            logSink = { Timber.tag("Http").d(it) },
        )
    return captureConnectionError {
          withContext(Dispatchers.IO) { connectionClient.health() }
          val previousSessionId = sessionId
          val session =
              withContext(Dispatchers.IO) {
                connectionClient.reuseOrCreateSession(existingSessionId = previousSessionId)
              }
          sessionId = session.sessionId
          onSaveSessionId(session.sessionId)
          if (previousSessionId.isNotBlank() && previousSessionId != session.sessionId) {
            withContext(Dispatchers.IO) {
              runCatching { connectionClient.deleteSession(previousSessionId) }
                  .onFailure { error ->
                    Timber.w(error, "Failed to delete stale demo session %s", previousSessionId)
                  }
            }
          }
          if (persistSuccess) {
            onSaveSuccessfulConfig(normalizedPort, normalizedToken)
          }
          currentDraftPort = normalizedPort
          currentDraftToken = normalizedToken
          onSaveDraftConfig(normalizedPort, normalizedToken)
          phase = StartupPhase.Connected
          recoveryReason = null
        }
        .also { error ->
          if (error != null) {
            sessionId = ""
            onSaveSessionId("")
          }
        }
  }

  LaunchedEffect(phase) {
    if (phase == StartupPhase.Reconnecting) {
      val error =
          establishConnection(lastSuccessfulPort, lastSuccessfulToken, persistSuccess = false)
      if (error != null) {
        recoveryReason = error
        currentDraftPort = draftPort.ifBlank { lastSuccessfulPort }
        currentDraftToken = draftToken.ifBlank { lastSuccessfulToken }
        phase = StartupPhase.Recovery
      }
    }
  }

  when {
    phase == StartupPhase.Initializing || phase == StartupPhase.Reconnecting -> {
      Column(
          modifier = Modifier.fillMaxSize(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
      ) {
        CircularProgressIndicator()
        Text(
            "Reconnecting…",
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
      }
    }

    phase == StartupPhase.ConfigRequired -> {
      ConfigScreen(
          port = currentDraftPort,
          token = currentDraftToken,
          onPortChange = {
            currentDraftPort = it
            onSaveDraftConfig(currentDraftPort, currentDraftToken)
          },
          onTokenChange = {
            currentDraftToken = it
            onSaveDraftConfig(currentDraftPort, currentDraftToken)
          },
          connectLabel = "Connect",
          onConnect = { p, t ->
            currentDraftPort = p.toString()
            currentDraftToken = t.trim()
            sessionId = ""
            onSaveDraftConfig(currentDraftPort, currentDraftToken)
            activePort = currentDraftPort
            activeToken = currentDraftToken
            phase = StartupPhase.ManualReconnect
          },
      )
    }

    phase == StartupPhase.Recovery -> {
      val shouldExposeToken = recoveryReason?.contains("401") == true
      ConfigScreen(
          port = currentDraftPort,
          token = currentDraftToken,
          onPortChange = {
            currentDraftPort = it
            onSaveDraftConfig(currentDraftPort, currentDraftToken)
          },
          onTokenChange = {
            currentDraftToken = it
            onSaveDraftConfig(currentDraftPort, currentDraftToken)
          },
          title = "Reconnect to AI Service",
          subtitle =
              "The last successful connection could not be reused. Update the port and retry. You can replace the token if needed.",
          errorMessage = recoveryReason,
          emphasizePortOverride = true,
          tokenOptionalInitiallyHidden = !shouldExposeToken,
          connectLabel = "Retry",
          resetLabel = "Reset All",
          onReset = {
            currentDraftPort = ""
            currentDraftToken = ""
            activePort = ""
            activeToken = ""
            sessionId = ""
            recoveryReason = null
            phase = StartupPhase.ConfigRequired
            onClearConfig()
          },
          onConnect = { p, t ->
            currentDraftPort = p.toString()
            currentDraftToken = t.trim()
            sessionId = ""
            onSaveDraftConfig(currentDraftPort, currentDraftToken)
            phase = StartupPhase.ManualReconnect
          },
      )
    }

    phase == StartupPhase.ManualReconnect -> {
      Column(
          modifier = Modifier.fillMaxSize(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
      ) {
        CircularProgressIndicator()
        Text(
            "Retrying connection…",
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
      }
    }

    phase == StartupPhase.Connected && sessionId.isBlank() -> {
      // Shouldn't normally happen, but handle gracefully
      Text(
          "Waiting for session…",
          modifier = Modifier.padding(24.dp),
      )
    }

    phase == StartupPhase.Connected && client != null -> {
      MainTabs(
          client = client,
          sessionId = sessionId,
          trackedJobIds = trackedJobIds,
          onSessionInvalid = {
            recreateDemoSession(
                client = client,
                previousSessionId = sessionId,
                onSaveSessionId = onSaveSessionId,
                onSessionChanged = { sessionId = it },
            )
          },
          logEntries = DemoLogEntries(logEntries),
          logSearchQuery = logSearchQuery,
          onLogSearchQueryChange = { logSearchQuery = it },
          onClearLogs = { application.logging.clear() },
      )
    }

    else -> Unit
  }

  LaunchedEffect(phase, currentDraftPort, currentDraftToken) {
    if (phase == StartupPhase.ManualReconnect) {
      val error =
          establishConnection(
              currentDraftPort,
              currentDraftToken,
              persistSuccess = true,
          )
      if (error != null) {
        recoveryReason = error
        phase = StartupPhase.Recovery
      }
    }
  }
}

private fun readDraftPort(prefs: android.content.SharedPreferences): String =
    prefs.getString(KEY_DRAFT_PORT, null) ?: prefs.getString(KEY_LAST_SUCCESS_PORT, "") ?: ""

private enum class StartupPhase {
  Initializing,
  ConfigRequired,
  Reconnecting,
  Recovery,
  ManualReconnect,
  Connected,
}

private suspend fun recreateDemoSession(
    client: AltioAiServiceClient,
    previousSessionId: String,
    onSaveSessionId: (String) -> Unit,
    onSessionChanged: (String) -> Unit,
): String {
  val session = client.createSession(appName = "Altio Demo")
  onSessionChanged(session.sessionId)
  onSaveSessionId(session.sessionId)
  if (previousSessionId.isNotBlank() && previousSessionId != session.sessionId) {
    runCatching { client.deleteSession(previousSessionId) }
        .onFailure { error ->
          Timber.w(error, "Failed to delete stale demo session %s", previousSessionId)
        }
  }
  return session.sessionId
}

internal suspend fun captureConnectionError(block: suspend () -> Unit): String? =
    try {
      block()
      null
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      e.toUserFacingConnectError()
    }

internal suspend fun AltioAiServiceClient.reuseOrCreateSession(
    existingSessionId: String,
): SessionResponse {
  if (existingSessionId.isNotBlank()) {
    val reused =
        runCatching { getSession(existingSessionId) }
            .getOrNull()
            ?.let {
              SessionResponse(it.sessionId, it.modelId, it.clientId, "Altio Demo", it.createdAt)
            }
    if (reused != null) return reused
  }
  return createSession(appName = "Altio Demo")
}

private fun Exception.toUserFacingConnectError(): String {
  val message = message.orEmpty()
  return when (this) {
    is UnauthorizedException -> "401 unauthorized. The saved bearer token was rejected."
    is ServiceUnavailableException -> "The service is reachable but not ready yet."
    is RateLimitedException -> apiError?.message ?: "The service is currently rate limited."
    is HttpFailureException ->
        "The service responded with ${statusCode ?: "an error"}. Check the port and service state."
    else ->
        when {
          message.contains("Connection refused", ignoreCase = true) ->
              "The service was not reachable on the saved port. Update the port and retry."
          message.contains("Failed to connect", ignoreCase = true) ->
              "The service was not reachable on the saved port. Update the port and retry."
          message.contains("timeout", ignoreCase = true) ->
              "The connection timed out. Check that the service app is running and update the port if needed."
          else -> "Connection failed: ${if (message.isBlank()) javaClass.simpleName else message}"
        }
  }
}

private val TABS = listOf("Chat", "Audio", "Jobs", "Health", "Log")

@Composable
private fun MainTabs(
    client: AltioAiServiceClient,
    sessionId: String,
    trackedJobIds: SnapshotStateList<String>,
    onSessionInvalid: suspend () -> String,
    logEntries: DemoLogEntries,
    logSearchQuery: String,
    onLogSearchQueryChange: (String) -> Unit,
    onClearLogs: () -> Unit,
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  val chatState = rememberChatConversationState(client, sessionId, trackedJobIds, onSessionInvalid)
  val audioState = rememberAudioScreenState(client, sessionId, trackedJobIds, onSessionInvalid)

  DisposableEffect(audioState) { onDispose { audioState.cleanup() } }

  val context = LocalContext.current
  val versionName = remember {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
  }

  Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      bottomBar = {
        Text(
            text = versionName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp),
        )
      },
  ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      PrimaryTabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
        TABS.forEachIndexed { index, label ->
          Tab(
              selected = selectedTab == index,
              onClick = { selectedTab = index },
              text = { Text(label) },
          )
        }
      }

      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when (selectedTab) {
          0 -> ChatScreen(chatState = chatState)
          1 -> AudioScreen(audioState = audioState)
          2 -> JobsScreen(client = client, trackedJobIds = trackedJobIds)
          3 -> HealthScreen(client = client)
          4 ->
              DebugLogsScreen(
                  entries = DebugLogEntries(logEntries.items),
                  searchQuery = logSearchQuery,
                  onSearchQueryChange = onLogSearchQueryChange,
                  versionName = versionName,
                  onTestCrash = {
                    Thread { throw RuntimeException("Test crash triggered from demo log tab") }
                        .start()
                  },
                  onClearLogs = onClearLogs,
              )
        }
      }
    }
  }
}
