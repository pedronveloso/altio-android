/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.altio.service.domain.model.DownloadStatus
import app.altio.service.domain.model.Model
import app.altio.service.domain.model.ModelStatus
import app.altio.service.domain.runtime.Accelerator
import app.altio.service.domain.runtime.GenerationConfig
import app.altio.service.domain.runtime.InferenceChunk
import app.altio.service.domain.runtime.InferenceRequest
import app.altio.service.domain.runtime.Message
import app.altio.service.domain.runtime.Part
import app.altio.service.domain.runtime.Role
import app.altio.service.domain.runtime.RuntimeConfig
import app.altio.service.domain.runtime.SessionParams
import app.altio.service.domain.settings.AppSettings
import app.altio.service.domain.settings.OnboardingCheckpoint
import app.altio.service.domain.settings.isOpenClAvailabilityFailure
import app.altio.service.domain.settings.resolveAccelerator
import app.altio.service.domain.settings.shouldLearnCpuForModel
import app.altio.service.download.shouldShowInterruptionWarning
import app.altio.service.logging.ui.DebugLogEntries
import app.altio.service.logging.ui.DebugLogsScreen
import app.altio.service.power.DevicePowerManager
import app.altio.service.power.OemGuidance
import app.altio.service.state.serviceLaunchBlockedFailure
import app.altio.service.ui.dashboard.DashboardModelManagementState
import app.altio.service.ui.dashboard.DashboardScreen
import app.altio.service.ui.dashboard.DashboardServiceStartFailure
import app.altio.service.ui.dashboard.DashboardState
import app.altio.service.ui.device.DeviceOemGuidanceUi
import app.altio.service.ui.model.ModelDownloadScreen
import app.altio.service.ui.model.ModelManagerDownloadProgressByModelId
import app.altio.service.ui.model.ModelManagerModels
import app.altio.service.ui.model.ModelManagerScreen
import app.altio.service.ui.session.ManagedJobItem
import app.altio.service.ui.session.ManagedSessionItem
import app.altio.service.ui.session.SessionManagementScreen
import app.altio.service.ui.session.SessionManagementState
import app.altio.service.ui.settings.AdvancedBenchmarkRuns
import app.altio.service.ui.settings.AdvancedSettingsScreen
import app.altio.service.ui.settings.BenchmarkRun
import app.altio.service.ui.settings.SettingsDownloadProgressByModelId
import app.altio.service.ui.settings.SettingsModels
import app.altio.service.ui.settings.SettingsScreen
import app.altio.service.ui.setup.PermissionsScreen
import app.altio.service.ui.setup.WelcomeScreen
import app.altio.service.ui.theme.AltioTheme
import app.altio.service.ui.token.TokenManagerScreen
import app.altio.service.ui.token.TokenManagerTokens
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private sealed interface Screen {
  data object Welcome : Screen

  data object Permissions : Screen

  data object ModelDownload : Screen

  data object Dashboard : Screen

  data object SessionManager : Screen

  data object ModelManager : Screen

  data object Settings : Screen

  data object Advanced : Screen

  data object TokenManager : Screen

  data object DebugLogs : Screen
}

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    NotificationPermissionHelper.ensureNotificationChannels(this)

    val appGraph = (application as AltioApplication).appGraph
    val devicePowerManager =
        DevicePowerManager(
            this,
            allowDirectBatteryExemptionRequest = BuildConfig.ALLOW_DIRECT_BATTERY_EXEMPTION_REQUEST,
        )

    setContent {
      AltioTheme {
        AppContent(
            appGraph = appGraph,
            devicePowerManager = devicePowerManager,
            onStartService = {
              runCatching {
                    startForegroundService(
                        Intent(this, AiBackgroundService::class.java)
                            .setAction(AiBackgroundService.ACTION_START_OR_RESTART)
                    )
                  }
                  .onFailure { error ->
                    val failure = serviceLaunchBlockedFailure(error)
                    appGraph.serviceStartFailureStore.setFailure(failure)
                    Timber.e(
                        error,
                        "%s %s: %s",
                        failure.code,
                        failure.title,
                        failure.technicalDetail,
                    )
                  }
            },
            onStopService = { stopService(Intent(this, AiBackgroundService::class.java)) },
            onOpenBatteryOptimizationSettings = {
              launchIntent(devicePowerManager.batteryOptimizationSettingsIntent())
            },
            onRequestDirectBatteryExemption = {
              devicePowerManager.directBatteryExemptionIntent()?.let(::launchIntent)
            },
            onOpenOemSettings = {
              launchFirstResolvableIntent(
                  devicePowerManager.oemGuidance()?.settingsIntents.orEmpty()
              )
            },
            onOpenNotificationSettings = {
              launchFirstResolvableIntent(
                  listOf(
                      NotificationPermissionHelper.appNotificationSettingsIntent(packageName),
                      NotificationPermissionHelper.appDetailsSettingsIntent(packageName),
                  )
              )
            },
        )
      }
    }
  }

  private fun launchIntent(intent: Intent) {
    runCatching { startActivity(intent) }
  }

  private fun launchFirstResolvableIntent(intents: List<Intent>) {
    intents.firstNotNullOfOrNull { intent ->
      runCatching {
            startActivity(intent)
            intent
          }
          .getOrNull()
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppContent(
    appGraph: AppGraph,
    devicePowerManager: DevicePowerManager,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onRequestDirectBatteryExemption: () -> Unit,
    onOpenOemSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val lifecycleOwner = LocalLifecycleOwner.current
  val context = LocalContext.current
  val activity = context as? Activity
  val modelState by appGraph.modelStateStore.state.collectAsState()
  val serverState by appGraph.serverStateStore.state.collectAsState()
  val settings = modelState.settings
  val onboardingProgress by
      appGraph.modelRepository.getDownloadProgress("gemma-4-e2b-it").collectAsState(null)
  var screen by remember { mutableStateOf<Screen>(Screen.Welcome) }
  var previousScreen by remember { mutableStateOf<Screen>(Screen.Welcome) }
  var initialised by remember { mutableStateOf(false) }
  var isBenchmarking by remember { mutableStateOf(false) }
  var benchmarkProgress by remember { mutableStateOf<String?>(null) }
  val benchmarkRuns = remember { mutableStateListOf<BenchmarkRun>() }
  val logEntries by appGraph.logging.entries.collectAsState()
  var logSearchQuery by remember { mutableStateOf("") }
  var batteryOptimizationDisabled by remember {
    mutableStateOf(devicePowerManager.isBatteryOptimizationDisabled())
  }
  var notificationsEnabled by remember {
    mutableStateOf(NotificationPermissionHelper.areRequiredNotificationsEnabled(context))
  }
  var notificationRequestAttempted by rememberSaveable { mutableStateOf(false) }
  var keepScreenAwake by rememberSaveable { mutableStateOf(true) }
  var lastDownloadBytes by remember {
    mutableLongStateOf(onboardingProgress?.bytesDownloaded ?: 0L)
  }
  var lastDownloadProgressAtMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
  var stallClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
  val supportsDirectBatteryExemption =
      remember(devicePowerManager) { devicePowerManager.directBatteryExemptionIntent() != null }
  val oemGuidance = remember(devicePowerManager) { devicePowerManager.oemGuidance()?.toUiModel() }
  val notificationLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationRequestAttempted = true
        notificationsEnabled = NotificationPermissionHelper.areRequiredNotificationsEnabled(context)
      }

  LaunchedEffect(Unit) {
    val s = appGraph.settingsRepository.settings.first()
    screen = startScreenFor(s, onboardingProgress)
    initialised = true
  }

  DisposableEffect(lifecycleOwner, devicePowerManager) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        batteryOptimizationDisabled = devicePowerManager.isBatteryOptimizationDisabled()
        notificationsEnabled = NotificationPermissionHelper.areRequiredNotificationsEnabled(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(onboardingProgress?.bytesDownloaded, onboardingProgress?.status) {
    val bytesDownloaded = onboardingProgress?.bytesDownloaded ?: 0L
    if (
        bytesDownloaded != lastDownloadBytes || onboardingProgress?.status == DownloadStatus.SUCCESS
    ) {
      lastDownloadBytes = bytesDownloaded
      lastDownloadProgressAtMs = System.currentTimeMillis()
    }
    if (onboardingProgress?.status == DownloadStatus.CANCELLED || onboardingProgress == null) {
      lastDownloadBytes = 0L
      lastDownloadProgressAtMs = System.currentTimeMillis()
    }
  }

  LaunchedEffect(onboardingProgress?.status) {
    while (
        onboardingProgress?.status == DownloadStatus.DOWNLOADING ||
            onboardingProgress?.status == DownloadStatus.QUEUED
    ) {
      stallClockMs = System.currentTimeMillis()
      delay(5_000)
    }
  }

  val currentProgress = onboardingProgress
  val showInterruptionWarning =
      shouldShowInterruptionWarning(
          progress = currentProgress,
          nowMs = stallClockMs,
          lastDownloadProgressAtMs = lastDownloadProgressAtMs,
          stalledThresholdMs = STALLED_DOWNLOAD_THRESHOLD_MS,
      )

  val keepScreenAwakeActive =
      screen == Screen.ModelDownload &&
          keepScreenAwake &&
          (onboardingProgress?.status == DownloadStatus.DOWNLOADING ||
              onboardingProgress?.status == DownloadStatus.QUEUED)

  DisposableEffect(activity, keepScreenAwakeActive) {
    if (keepScreenAwakeActive) {
      activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
  }

  if (!initialised) return

  fun navigateTo(target: Screen) {
    screen = target
    val checkpoint =
        when (target) {
          Screen.Welcome -> OnboardingCheckpoint.WELCOME
          Screen.Permissions -> OnboardingCheckpoint.PERMISSIONS
          Screen.ModelDownload -> OnboardingCheckpoint.MODEL_DOWNLOAD
          Screen.Dashboard -> OnboardingCheckpoint.COMPLETE
          Screen.SessionManager,
          Screen.ModelManager,
          Screen.Settings,
          Screen.Advanced,
          Screen.TokenManager,
          Screen.DebugLogs -> null
        }
    checkpoint?.let { nextCheckpoint ->
      scope.launch { appGraph.settingsRepository.setOnboardingCheckpoint(nextCheckpoint) }
    }
  }

  val backTarget = previousScreenFor(screen, previousScreen)
  BackHandler(enabled = backTarget != null) { backTarget?.let(::navigateTo) }

  Scaffold(
      modifier = Modifier.fillMaxSize(),
      topBar = {
        val titleText =
            when (screen) {
              Screen.Settings -> "Settings"
              Screen.Advanced -> "Advanced"
              Screen.TokenManager -> "Manage API Tokens"
              Screen.ModelManager -> "Manage Models"
              Screen.DebugLogs -> "Debug Logs"
              else -> null
            }
        if (titleText != null || BuildConfig.DEBUG || screen == Screen.Dashboard) {
          TopAppBar(
              title = {
                if (screen == Screen.Dashboard) {
                  Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Text(
                        text = "Altio Service Server",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    val statusText = if (serverState.serverRunning) "Running" else "Stopped"
                    val statusColor =
                        if (serverState.serverRunning) {
                          MaterialTheme.colorScheme.primary
                        } else {
                          MaterialTheme.colorScheme.error
                        }

                    Surface(
                        color = statusColor.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.extraSmall,
                        border =
                            androidx.compose.foundation.BorderStroke(
                                1.dp,
                                statusColor.copy(alpha = 0.3f),
                            ),
                    ) {
                      Row(
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(4.dp),
                      ) {
                        Surface(
                            modifier = Modifier.size(6.dp),
                            shape = MaterialTheme.shapes.extraSmall,
                            color = statusColor,
                            content = {},
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                        )
                      }
                    }
                  }
                } else if (titleText != null) {
                  Text(titleText)
                }
              },
              navigationIcon = {
                if (backTarget != null) {
                  IconButton(onClick = { backTarget.let(::navigateTo) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                  }
                }
              },
              actions = {
                if (BuildConfig.DEBUG && screen != Screen.DebugLogs) {
                  IconButton(
                      onClick = {
                        previousScreen = screen
                        screen = Screen.DebugLogs
                      }
                  ) {
                    Icon(
                        imageVector = Icons.Filled.BugReport,
                        contentDescription = "Debug logs",
                    )
                  }
                }
              },
          )
        }
      },
  ) { innerPadding ->
    when (screen) {
      Screen.Welcome ->
          WelcomeScreen(
              onGetStarted = { navigateTo(Screen.Permissions) },
              versionName = BuildConfig.VERSION_NAME,
              modifier = Modifier.padding(innerPadding),
          )

      Screen.Permissions ->
          PermissionsScreen(
              batteryOptimizationDisabled = batteryOptimizationDisabled,
              notificationsEnabled = notificationsEnabled,
              notificationRequestAttempted = notificationRequestAttempted,
              supportsDirectBatteryExemption = supportsDirectBatteryExemption,
              oemGuidance = oemGuidance,
              onRequestNotifications = {
                notificationLauncher.launch(
                    NotificationPermissionHelper.postNotificationsPermission
                )
              },
              onOpenNotificationSettings = onOpenNotificationSettings,
              onOpenBatteryOptimizationSettings = {
                scope.launch {
                  appGraph.settingsRepository.setBatteryOptimizationGuidanceSeen(true)
                }
                onOpenBatteryOptimizationSettings()
              },
              onRequestDirectBatteryExemption = {
                scope.launch {
                  appGraph.settingsRepository.setBatteryOptimizationGuidanceSeen(true)
                }
                onRequestDirectBatteryExemption()
              },
              onOpenOemSettings = {
                scope.launch { appGraph.settingsRepository.setOemGuidanceSeen(true) }
                onOpenOemSettings()
              },
              onContinue = { navigateTo(Screen.ModelDownload) },
              modifier = Modifier.padding(innerPadding),
          )

      Screen.ModelDownload -> {
        ModelDownloadScreen(
            modelName = "Gemma 4 E2B IT",
            progress = onboardingProgress,
            batteryOptimizationDisabled = batteryOptimizationDisabled,
            supportsDirectBatteryExemption = supportsDirectBatteryExemption,
            oemGuidance = oemGuidance,
            keepScreenAwake = keepScreenAwake,
            showInterruptionWarning = showInterruptionWarning,
            onKeepScreenAwakeChange = { keepScreenAwake = it },
            onOpenBatteryOptimizationSettings = {
              scope.launch { appGraph.settingsRepository.setBatteryOptimizationGuidanceSeen(true) }
              onOpenBatteryOptimizationSettings()
            },
            onRequestDirectBatteryExemption = {
              scope.launch { appGraph.settingsRepository.setBatteryOptimizationGuidanceSeen(true) }
              onRequestDirectBatteryExemption()
            },
            onOpenOemSettings = {
              scope.launch { appGraph.settingsRepository.setOemGuidanceSeen(true) }
              onOpenOemSettings()
            },
            onRestartDownloadWorker = {
              scope.launch {
                appGraph.modelRepository.pauseDownload("gemma-4-e2b-it")
                appGraph.modelRepository.resumeDownload("gemma-4-e2b-it")
              }
            },
            onDownloadClick = {
              scope.launch { appGraph.modelRepository.startDownload("gemma-4-e2b-it") }
            },
            onUseDemoModelClick = {
              scope.launch {
                appGraph.settingsRepository.setActiveModelId(DEMO_MODEL_ID)
                appGraph.settingsRepository.setSetupComplete(true)
                appGraph.settingsRepository.setOnboardingCheckpoint(OnboardingCheckpoint.COMPLETE)
                onStartService()
                navigateTo(Screen.Dashboard)
              }
            },
            onPauseClick = {
              scope.launch { appGraph.modelRepository.pauseDownload("gemma-4-e2b-it") }
            },
            onResumeClick = {
              scope.launch { appGraph.modelRepository.resumeDownload("gemma-4-e2b-it") }
            },
            onCancelClick = {
              scope.launch { appGraph.modelRepository.cancelDownload("gemma-4-e2b-it") }
            },
            modifier = Modifier.padding(innerPadding),
        )

        LaunchedEffect(onboardingProgress?.status) {
          val status = onboardingProgress?.status ?: return@LaunchedEffect
          if (status == DownloadStatus.SUCCESS) {
            appGraph.settingsRepository.setSetupComplete(true)
            appGraph.settingsRepository.setOnboardingCheckpoint(OnboardingCheckpoint.COMPLETE)
            onStartService()
            navigateTo(Screen.Dashboard)
          }
        }
      }

      Screen.Dashboard -> {
        val activeModel = modelState.activeModel
        var isModelTestRunning by remember(settings.activeModelId) { mutableStateOf(false) }
        var modelTestPassed by remember(settings.activeModelId) { mutableStateOf(false) }
        var modelTestMessage by remember(settings.activeModelId) { mutableStateOf<String?>(null) }

        DashboardScreen(
            state =
                DashboardState(
                    serverRunning = serverState.serverRunning,
                    port = serverState.port,
                    modelId = settings.activeModelId,
                    modelReady =
                        activeModel?.status == ModelStatus.READY ||
                            activeModel?.status == ModelStatus.LOADED,
                    modelLoaded = modelState.loadedModelId == settings.activeModelId,
                    modelManagement =
                        DashboardModelManagementState(
                            actionNeeded = modelState.modelManagementSummary.actionNeeded,
                            statusMessage = modelState.modelManagementSummary.statusMessage,
                            detailMessage = modelState.modelManagementSummary.detailMessage,
                            attentionCount = modelState.modelManagementSummary.attentionCount,
                        ),
                    activeSessions = serverState.activeSessionCount,
                    activeJobs = serverState.activeJobCount,
                    uptimeSeconds = serverState.uptimeSeconds,
                    isModelTestRunning = isModelTestRunning,
                    modelTestPassed = modelTestPassed,
                    modelTestMessage = modelTestMessage,
                    serviceStartFailure =
                        serverState.startFailure?.let { failure ->
                          DashboardServiceStartFailure(
                              code = failure.code,
                              title = failure.title,
                              description = failure.description,
                          )
                        },
                ),
            onStartServer = onStartService,
            onStopServer = onStopService,
            onRunModelTest = {
              activeModel?.let { model ->
                scope.launch {
                  isModelTestRunning = true
                  modelTestPassed = false
                  modelTestMessage = null

                  runCatching { runDashboardModelSmokeTest(model, settings, appGraph) }
                      .onSuccess {
                        modelTestPassed = true
                        modelTestMessage = "Model loaded and responded successfully."
                      }
                      .onFailure { error ->
                        modelTestPassed = false
                        modelTestMessage =
                            error.message?.let { "Model test failed: $it" } ?: "Model test failed."
                      }

                  isModelTestRunning = false
                }
              }
            },
            onUnloadModel = { scope.launch { appGraph.engineHolder.unload() } },
            onNavigateToSessionManager = { screen = Screen.SessionManager },
            onNavigateToModelManager = { screen = Screen.ModelManager },
            onNavigateToSettings = { screen = Screen.Settings },
            modifier = Modifier.padding(innerPadding),
        )
      }

      Screen.SessionManager -> {
        val sessions by appGraph.sessionRepository.observeAllSessions().collectAsState(emptyList())
        val activeJobs by appGraph.jobRepository.observeActiveJobs().collectAsState(emptyList())
        SessionManagementScreen(
            state =
                SessionManagementState(
                    sessions =
                        sessions.map { session ->
                          ManagedSessionItem(
                              sessionId = session.id,
                              appName = session.appName,
                              modelId = session.modelId,
                              messageCount = session.messageCount,
                              createdAt = session.createdAt,
                              lastActiveAt = session.lastActiveAt,
                          )
                        },
                    activeJobs =
                        activeJobs.map { job ->
                          ManagedJobItem(
                              jobId = job.id,
                              sessionId = job.sessionId,
                              appName = job.appName,
                              type = job.type,
                              status = job.status,
                              createdAt = job.createdAt,
                          )
                        },
                ),
            onCancelSession = { sessionId ->
              scope.launch { cancelManagedSession(appGraph, sessionId) }
            },
            onCancelJob = { jobId -> appGraph.inferenceScheduler.cancelJob(jobId) },
            modifier = Modifier.padding(innerPadding),
        )
      }

      Screen.Settings ->
          SettingsScreen(
              settings = settings,
              isModelsLoading = !modelState.modelsLoaded,
              models = SettingsModels(modelState.models),
              downloadProgressByModelId =
                  SettingsDownloadProgressByModelId(modelState.downloadProgressByModelId),
              onSetStartOnBoot = {
                scope.launch { appGraph.settingsRepository.setStartOnBoot(it) }
              },
              onSetIdleShutdown = {
                scope.launch { appGraph.settingsRepository.setIdleShutdownMinutes(it) }
              },
              onSetServerPort = { scope.launch { appGraph.settingsRepository.setServerPort(it) } },
              onSetAccelerator = {
                scope.launch { appGraph.settingsRepository.setAccelerator(it) }
              },
              onSetMaxTokens = { scope.launch { appGraph.settingsRepository.setMaxTokens(it) } },
              onNavigateToModelManager = { screen = Screen.ModelManager },
              onNavigateToTokenManager = { screen = Screen.TokenManager },
              onNavigateToAdvanced = { navigateTo(Screen.Advanced) },
              modifier = Modifier.padding(innerPadding),
          )

      Screen.Advanced -> {
        val cpuInfo = remember { HardwareInfo.getCpuInfo() }
        val gpuInfo = remember { HardwareInfo.getGpuInfo() }
        val ramInfo = remember { HardwareInfo.getRamInfo(context) }
        val activeModel =
            modelState.models.firstOrNull { it.definition.id == settings.activeModelId }
        val isModelReady =
            activeModel != null &&
                (activeModel.status == ModelStatus.READY ||
                    activeModel.status == ModelStatus.LOADED)
        AdvancedSettingsScreen(
            cpuInfo = cpuInfo,
            gpuInfo = gpuInfo,
            ramInfo = ramInfo,
            isModelReady = isModelReady,
            modelName = activeModel?.definition?.name,
            isBenchmarking = isBenchmarking,
            benchmarkProgress = benchmarkProgress,
            benchmarkRuns = AdvancedBenchmarkRuns(benchmarkRuns),
            onStartBenchmark = {
              activeModel?.let { model ->
                scope.launch {
                  isBenchmarking = true
                  benchmarkProgress = "Starting benchmark..."
                  try {
                    val resolvedAccelerator = settings.resolveAccelerator(model)
                    val config =
                        RuntimeConfig(
                            accelerator = resolvedAccelerator,
                            maxTokens = settings.maxTokens,
                        )

                    checkDashboardModelSmokeTestAvailable(
                        appGraph.sessionManager.activeSessionCount.value
                    )

                    benchmarkProgress = "Unloading existing model..."
                    withContext(Dispatchers.IO) { appGraph.engineHolder.unload() }

                    // Let GC clear old model references from memory
                    System.gc()
                    System.runFinalization()
                    kotlinx.coroutines.delay(200)
                    val baselineMemory = android.os.Debug.getNativeHeapAllocatedSize()

                    benchmarkProgress = "Running warmup iteration 1/3..."
                    runBenchmarkIteration(model, config, appGraph)

                    benchmarkProgress = "Running iteration 2/3..."
                    val start2 = System.currentTimeMillis()
                    runBenchmarkIteration(model, config, appGraph)
                    val duration2 = System.currentTimeMillis() - start2

                    benchmarkProgress = "Running iteration 3/3..."
                    val start3 = System.currentTimeMillis()
                    runBenchmarkIteration(model, config, appGraph)
                    val duration3 = System.currentTimeMillis() - start3

                    val avg = (duration2 + duration3) / 2.0
                    benchmarkProgress = "Benchmark complete!"

                    // Measure active native memory usage
                    val activeMemory = android.os.Debug.getNativeHeapAllocatedSize()
                    val modelMemoryBytes = activeMemory - baselineMemory
                    val memoryUsageString =
                        if (modelMemoryBytes > 0) {
                          formatBytes(modelMemoryBytes)
                        } else {
                          formatBytes(activeMemory)
                        }

                    val isCpuLearned = settings.modelAccelerators[model.definition.id] == "CPU"
                    val isModelCpuOnly =
                        model.definition.defaultConfig.accelerators.none {
                          it.equals("gpu", ignoreCase = true)
                        }
                    val fallbackStatus =
                        when {
                          isCpuLearned ->
                              "Device fell back to CPU acceleration (GPU initialization failed)."
                          resolvedAccelerator == Accelerator.CPU || isModelCpuOnly ->
                              "Device is using CPU acceleration."
                          else -> "Device is capable of using GPU acceleration."
                        }

                    val run =
                        BenchmarkRun(
                            id = UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            modelId = model.definition.id,
                            modelName = model.definition.name,
                            accelerator = resolvedAccelerator.name,
                            maxTokens = settings.maxTokens,
                            avgLatencyMs = avg,
                            run2LatencyMs = duration2,
                            run3LatencyMs = duration3,
                            fallbackStatus = fallbackStatus,
                            memoryUsage = memoryUsageString,
                        )
                    benchmarkRuns.add(0, run)
                  } catch (e: Exception) {
                    benchmarkProgress = "Benchmark failed: ${e.message}"
                  } finally {
                    isBenchmarking = false
                  }
                }
              }
            },
            modifier = Modifier.padding(innerPadding),
        )
      }

      Screen.ModelManager ->
          ModelManagerScreen(
              isModelsLoading = !modelState.modelsLoaded,
              models = ModelManagerModels(modelState.models),
              downloadProgressByModelId =
                  ModelManagerDownloadProgressByModelId(modelState.downloadProgressByModelId),
              activeModelId = settings.activeModelId,
              onSetActiveModel = {
                scope.launch { appGraph.settingsRepository.setActiveModelId(it) }
              },
              onDownloadClick = { modelId ->
                scope.launch { appGraph.modelRepository.startDownload(modelId) }
              },
              onPauseDownload = { modelId ->
                scope.launch { appGraph.modelRepository.pauseDownload(modelId) }
              },
              onResumeDownload = { modelId ->
                scope.launch { appGraph.modelRepository.resumeDownload(modelId) }
              },
              onCancelDownload = { modelId ->
                scope.launch { appGraph.modelRepository.cancelDownload(modelId) }
              },
              onDeleteModel = { modelId ->
                scope.launch { appGraph.modelRepository.deleteModel(modelId) }
              },
              modifier = Modifier.padding(innerPadding),
          )

      Screen.TokenManager -> {
        val tokens by appGraph.tokenRepository.observeTokens().collectAsState(emptyList())
        TokenManagerScreen(
            tokens = TokenManagerTokens(tokens),
            onGenerate = { label -> appGraph.tokenRepository.generateToken(label) },
            onRevoke = { hash -> appGraph.tokenRepository.revokeToken(hash) },
            modifier = Modifier.padding(innerPadding),
        )
      }

      Screen.DebugLogs ->
          DebugLogsScreen(
              entries = DebugLogEntries(logEntries),
              searchQuery = logSearchQuery,
              onSearchQueryChange = { logSearchQuery = it },
              versionName = BuildConfig.VERSION_NAME,
              onTestCrash = {
                // Crash on a background thread so the UncaughtExceptionHandler fires and
                // writes crash_report.txt before the process dies.
                Thread { throw RuntimeException("Test crash triggered from DebugLogsScreen") }
                    .start()
              },
              onClearLogs = { appGraph.logging.clear() },
              modifier = Modifier.padding(innerPadding),
          )
    }
  }
}

private fun OemGuidance.toUiModel(): DeviceOemGuidanceUi =
    DeviceOemGuidanceUi(
        title = title,
        summary = summary,
        steps = steps.map { "${it.title}: ${it.detail}" },
    )

private const val STALLED_DOWNLOAD_THRESHOLD_MS = 2 * 60 * 1_000L
private const val DEMO_MODEL_ID = "demo-model"
private const val DASHBOARD_MODEL_TEST_PROMPT =
    "Reply with one short sentence so I can verify the model loaded."

private fun startScreenFor(
    settings: AppSettings,
    progress: app.altio.service.domain.model.DownloadProgress?,
): Screen =
    when {
      settings.setupComplete || settings.onboardingCheckpoint == OnboardingCheckpoint.COMPLETE ->
          Screen.Dashboard
      progress != null && progress.status != DownloadStatus.SUCCESS -> Screen.ModelDownload
      settings.onboardingCheckpoint == OnboardingCheckpoint.MODEL_DOWNLOAD -> Screen.ModelDownload
      settings.onboardingCheckpoint == OnboardingCheckpoint.PERMISSIONS -> Screen.Permissions
      else -> Screen.Welcome
    }

private fun previousScreenFor(current: Screen, previous: Screen): Screen? =
    when (current) {
      Screen.DebugLogs -> previous
      Screen.SessionManager -> Screen.Dashboard
      Screen.ModelManager -> Screen.Dashboard
      Screen.TokenManager -> Screen.Settings
      Screen.Advanced -> Screen.Settings
      Screen.Settings -> Screen.Dashboard
      Screen.ModelDownload -> Screen.Permissions
      Screen.Permissions -> Screen.Welcome
      Screen.Welcome,
      Screen.Dashboard -> null
    }

private suspend fun cancelManagedSession(appGraph: AppGraph, sessionId: String) {
  val activeJobs =
      appGraph.jobRepository.observeActiveJobs().first().filter { it.sessionId == sessionId }
  activeJobs.forEach { job -> appGraph.inferenceScheduler.cancelJob(job.id) }
  appGraph.sessionCoordinator.deleteSession(sessionId)
}

private suspend fun runDashboardModelSmokeTest(
    model: Model,
    settings: AppSettings,
    appGraph: AppGraph,
): String {
  check(model.status == ModelStatus.READY || model.status == ModelStatus.LOADED) {
    "Model is not ready."
  }
  checkDashboardModelSmokeTestAvailable(appGraph.sessionManager.activeSessionCount.value)

  val resolvedAccelerator = settings.resolveAccelerator(model)
  val config = RuntimeConfig(accelerator = resolvedAccelerator, maxTokens = settings.maxTokens)
  Timber.i(
      "Dashboard Test Model starting for %s with accelerator=%s prompt=%s",
      model.definition.id,
      resolvedAccelerator,
      DASHBOARD_MODEL_TEST_PROMPT,
  )

  return runCatching { runDashboardModelSmokeTestAttempt(model, config, appGraph) }
      .recoverCatching { error ->
        val shouldRetryOnCpu =
            settings.shouldLearnCpuForModel(model.definition.id) &&
                resolvedAccelerator != Accelerator.CPU &&
                error.isOpenClAvailabilityFailure()
        if (!shouldRetryOnCpu) throw error

        Timber.w(
            error,
            "Dashboard Test Model failed for %s on %s; retrying on CPU",
            model.definition.id,
            resolvedAccelerator,
        )
        val response =
            runDashboardModelSmokeTestAttempt(
                model = model,
                config = config.copy(accelerator = Accelerator.CPU),
                appGraph = appGraph,
            )
        appGraph.settingsRepository.setModelAccelerator(model.definition.id, "CPU")
        Timber.i(
            "Dashboard Test Model learned CPU accelerator for %s after successful retry",
            model.definition.id,
        )
        response
      }
      .getOrThrow()
}

private suspend fun runDashboardModelSmokeTestAttempt(
    model: Model,
    config: RuntimeConfig,
    appGraph: AppGraph,
): String {
  val engine = appGraph.engineHolder.load(model = model, config = config)
  val session =
      engine.createSession(
          sessionId = "dashboard-smoke-test-${UUID.randomUUID()}",
          params =
              SessionParams(
                  generationConfig =
                      GenerationConfig(
                          maxTokens = minOf(config.maxTokens, 128),
                      )
              ),
      )
  val response = StringBuilder()

  try {
    session
        .generateStream(
            InferenceRequest(
                messages =
                    listOf(
                        Message(
                            role = Role.USER,
                            parts = listOf(Part.Text(DASHBOARD_MODEL_TEST_PROMPT)),
                        )
                    ),
                config = GenerationConfig(maxTokens = minOf(config.maxTokens, 128)),
            )
        )
        .collect { chunk ->
          when (chunk) {
            is InferenceChunk.Token -> response.append(chunk.text)
            is InferenceChunk.Error -> throw chunk.cause
            is InferenceChunk.Done -> Unit
          }
        }
  } finally {
    session.close()
    // Engine is owned by engineHolder and stays loaded after the test.
  }

  val responseText = response.toString().trim().ifBlank { error("No text response received.") }
  Timber.i(
      "Dashboard Test Model response for %s with accelerator=%s: %s",
      model.definition.id,
      config.accelerator,
      responseText,
  )
  return responseText
}

internal fun checkDashboardModelSmokeTestAvailable(activeSessionCount: Int) {
  check(activeSessionCount == 0) { "Close active sessions before testing the model." }
}

private const val BENCHMARK_PROMPT = "Explain the concept of gravity in exactly three sentences."

private suspend fun runBenchmarkIteration(
    model: Model,
    config: RuntimeConfig,
    appGraph: AppGraph,
): String {
  val engine = appGraph.engineHolder.load(model = model, config = config)
  val session =
      engine.createSession(
          sessionId = "benchmark-${UUID.randomUUID()}",
          params =
              SessionParams(
                  generationConfig =
                      GenerationConfig(
                          maxTokens = minOf(config.maxTokens, 128),
                      )
              ),
      )
  val response = StringBuilder()
  try {
    session
        .generateStream(
            InferenceRequest(
                messages =
                    listOf(
                        Message(
                            role = Role.USER,
                            parts = listOf(Part.Text(BENCHMARK_PROMPT)),
                        )
                    ),
                config = GenerationConfig(maxTokens = minOf(config.maxTokens, 128)),
            )
        )
        .collect { chunk ->
          when (chunk) {
            is InferenceChunk.Token -> response.append(chunk.text)
            is InferenceChunk.Error -> throw chunk.cause
            is InferenceChunk.Done -> Unit
          }
        }
  } finally {
    session.close()
  }
  return response.toString().trim()
}

private fun formatBytes(bytes: Long): String {
  val absoluteBytes = java.lang.Math.abs(bytes)
  return when {
    absoluteBytes >= 1024 * 1024 * 1024L ->
        String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    absoluteBytes >= 1024 * 1024L ->
        String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    absoluteBytes >= 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes Bytes"
  }
}
