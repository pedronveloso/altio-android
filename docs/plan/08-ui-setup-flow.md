# 08 — UI & Setup Flow

## Stack

- **Jetpack Compose** + **Material 3** throughout
- **Single-activity** (`MainActivity`) hosting a Compose navigation graph
- **`@HiltViewModel`** for all ViewModels
- **`StateFlow` + `collectAsStateWithLifecycle`** for UI state

---

## Navigation Graph

```
SetupGraph (shown on first launch until setup complete)
  ├── WelcomeScreen
  ├── PermissionsScreen
  └── ModelDownloadScreen

MainGraph (shown after setup)
  ├── DashboardScreen        ← start destination
  ├── ModelManagerScreen
  ├── SettingsScreen
  │    └── TokenManagerScreen
  └── AboutScreen
```

First-launch detection: DataStore `settings.setupComplete: Boolean`. If `false`, `MainActivity` navigates to `SetupGraph`.

---

## Setup Flow

### WelcomeScreen

- App name + tagline
- Brief explanation of what the service does ("Runs AI locally on your device")
- **"Get Started"** button → `PermissionsScreen`

### PermissionsScreen

Requests required permissions in order:

| Permission | Reason shown to user |
|------------|---------------------|
| `POST_NOTIFICATIONS` | Show download progress and server status |
| `READ/WRITE_EXTERNAL_STORAGE` (API < 29) | Store model files (not needed on API ≥ 29) |

Uses `rememberPermissionState` (Accompanist-style or first-party `ActivityResultContracts`).
Each permission shows a rationale card before the system dialog.

"Continue" enabled only when all required permissions granted. Optional permissions can be skipped.

### ModelDownloadScreen

- Shows **Gemma 4 E2B IT** as the only option in v1
- Displays model size (~2.4 GB), capabilities badge (Text / Vision / Audio / Thinking)
- Shows RAM requirement warning if device has < 8 GB RAM
- **"Download"** button triggers `ModelRepository.startDownload("gemma-4-e2b-it")`
- Progress bar + speed + ETA driven by `Flow<DownloadProgress>`
- On completion: checkmark animation → **"Finish Setup"** → navigates to `DashboardScreen`, writes `setupComplete = true`
- Can be retried on failure; resumes from where it left off

---

## Main Screens

### DashboardScreen

Primary screen shown after setup.

```
┌─────────────────────────────────┐
│  Service Status                 │
│  ● Running  │ Port 54231        │
│  [Stop]                         │
├─────────────────────────────────┤
│  Model                          │
│  Gemma 3n E2B IT INT4  ● Loaded │
├─────────────────────────────────┤
│  Active Sessions    3           │
│  Active Jobs        1           │
│  Uptime             00:32:14    │
├─────────────────────────────────┤
│  Server URL (tap to copy)       │
│  http://127.0.0.1:54231/v1      │
└─────────────────────────────────┘
```

- Service can be started/stopped from here
- Port shown updates reactively via `server.port.collectAsStateWithLifecycle()`

### ModelManagerScreen

- Lists all available models (from `models.json`)
- Per model: name, size, status chip, capabilities tags
- Actions: **Download**, **Delete**, **Set as Active**
- Download shows inline progress card (same as setup screen)

### SettingsScreen

| Setting | Type | Default |
|---------|------|---------|
| Server enabled on boot | Switch | Off |
| Idle shutdown timer | Dropdown (2m, 5m, 10m, 30m, Never) | 10m |
| Accelerator | Dropdown (Auto, CPU, GPU) | Auto |
| Max tokens | Slider 256–8192 | 2048 |

Navigates to **TokenManagerScreen** for bearer token management.

### TokenManagerScreen

- Lists all active tokens (label + last used date)
- **Generate Token** → bottom sheet with generated token + copy button + label field
- **Revoke** action with confirmation dialog
- Revoked tokens are soft-deleted (marked `isRevoked = true`) and shown greyed out

---

## ViewModels

### `SetupViewModel`

```kotlin
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val downloadState: StateFlow<DownloadState> = modelRepository
        .getDownloadProgress("gemma-3n-e2b-it-int4")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DownloadState.Idle)

    fun startDownload() = viewModelScope.launch {
        modelRepository.startDownload("gemma-3n-e2b-it-int4")
    }

    fun completeSetup() = viewModelScope.launch {
        settingsRepository.setSetupComplete(true)
    }
}
```

### `DashboardViewModel`

```kotlin
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val server: AiHttpServer,
    private val diagnosticsRepository: DiagnosticsRepository,
) : ViewModel() {
    val serverPort = server.port.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val diagnostics = diagnosticsRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Diagnostics.empty())

    fun startServer() = viewModelScope.launch { server.start() }
    fun stopServer() = viewModelScope.launch { server.stop() }
}
```

---

## Design Tokens

Use Material 3 dynamic color (Android 12+) with a static seed color fallback.

Seed color: **`#1A6B5A`** (teal-green — evokes on-device, local, private).

Typography: use M3 defaults (`displayLarge` for server status, `bodyMedium` for metadata).

---

## Compose UI Tests (`:ui`)

```kotlin
@Test
fun downloadScreen_showsProgressWhenDownloading() {
    composeTestRule.setContent {
        ModelDownloadScreen(
            state = DownloadState.Downloading(progress = 0.42f, bytesPerSecond = 5_000_000, etaMs = 120_000),
            onDownload = {},
            onFinish = {},
        )
    }
    composeTestRule.onNodeWithText("42%").assertIsDisplayed()
    composeTestRule.onNodeWithText("4.8 MB/s").assertIsDisplayed()
}
```
