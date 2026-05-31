/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.ui.dashboard

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class DashboardScreenTest {

  private val runningState =
      DashboardState(
          serverRunning = true,
          port = 54231,
          modelId = "gemma-3n",
          modelReady = true,
          modelLoaded = true,
          modelManagement = DashboardModelManagementState(),
          activeSessions = 3,
          activeJobs = 1,
          uptimeSeconds = 1934L,
      )

  @Test
  fun dashboard_showsRunningStatusAndPort() = runComposeUiTest {
    setContent {
      DashboardScreen(
          state = runningState,
          onStartServer = {},
          onStopServer = {},
          onRunModelTest = {},
          onUnloadModel = {},
          onNavigateToSessionManager = {},
          onNavigateToModelManager = {},
          onNavigateToSettings = {},
      )
    }

    onNodeWithText("Running").assertIsDisplayed()
    onNodeWithText("Port 54231").assertIsDisplayed()
  }

  @Test
  fun dashboard_showsStopButtonWhenRunning() = runComposeUiTest {
    setContent {
      DashboardScreen(
          state = runningState,
          onStartServer = {},
          onStopServer = {},
          onRunModelTest = {},
          onUnloadModel = {},
          onNavigateToSessionManager = {},
          onNavigateToModelManager = {},
          onNavigateToSettings = {},
      )
    }

    onNodeWithText("Stop").assertIsDisplayed()
  }

  @Test
  fun dashboard_showsStartButtonWhenStopped() = runComposeUiTest {
    setContent {
      DashboardScreen(
          state = runningState.copy(serverRunning = false, port = null),
          onStartServer = {},
          onStopServer = {},
          onRunModelTest = {},
          onUnloadModel = {},
          onNavigateToSessionManager = {},
          onNavigateToModelManager = {},
          onNavigateToSettings = {},
      )
    }

    onNodeWithText("Start").assertIsDisplayed()
    onNodeWithText("Stopped").assertIsDisplayed()
  }

  @Test
  fun dashboard_showsServiceStartFailureAboveStoppedStatus() = runComposeUiTest {
    setContent {
      DashboardScreen(
          state =
              runningState.copy(
                  serverRunning = false,
                  port = null,
                  serviceStartFailure =
                      DashboardServiceStartFailure(
                          code = "SVC001",
                          title = "Port unavailable",
                          description =
                              "Port 52731 is already in use. Change the port in Settings or stop the other process.",
                      ),
              ),
          onStartServer = {},
          onStopServer = {},
          onRunModelTest = {},
          onUnloadModel = {},
          onNavigateToSessionManager = {},
          onNavigateToModelManager = {},
          onNavigateToSettings = {},
      )
    }

    onNodeWithText("SVC001").assertIsDisplayed()
    onNodeWithText("Port unavailable").assertIsDisplayed()
    onNodeWithText(
            "Port 52731 is already in use. Change the port in Settings or stop the other process."
        )
        .assertIsDisplayed()
    onNodeWithText("Stopped").assertIsDisplayed()
    onNodeWithText("Start").assertIsDisplayed()
  }

  @Test
  fun dashboard_showsServiceStartFailureWhileRunning() = runComposeUiTest {
    setContent {
      DashboardScreen(
          state =
              runningState.copy(
                  serviceStartFailure =
                      DashboardServiceStartFailure(
                          code = "SVC001",
                          title = "Port unavailable",
                          description =
                              "Port 52732 is already in use. Change the port in Settings or stop the other process.",
                      ),
              ),
          onStartServer = {},
          onStopServer = {},
          onRunModelTest = {},
          onUnloadModel = {},
          onNavigateToSessionManager = {},
          onNavigateToModelManager = {},
          onNavigateToSettings = {},
      )
    }

    onNodeWithText("SVC001").assertIsDisplayed()
    onNodeWithText("Running").assertIsDisplayed()
    onNodeWithText("Port 54231").assertIsDisplayed()
  }

  @Test
  fun dashboard_showsServerUrlWhenRunning() = runComposeUiTest {
    setContent {
      DashboardScreen(
          state = runningState,
          onStartServer = {},
          onStopServer = {},
          onRunModelTest = {},
          onUnloadModel = {},
          onNavigateToSessionManager = {},
          onNavigateToModelManager = {},
          onNavigateToSettings = {},
      )
    }

    onNodeWithText("http://127.0.0.1:54231/v1").assertIsDisplayed()
  }

  @Test
  fun dashboard_showsModelTestButtonWhenModelIsActive() = runComposeUiTest {
    setContent {
      DashboardScreen(
          state = runningState,
          onStartServer = {},
          onStopServer = {},
          onRunModelTest = {},
          onUnloadModel = {},
          onNavigateToSessionManager = {},
          onNavigateToModelManager = {},
          onNavigateToSettings = {},
      )
    }

    onNodeWithText("Test Model").assertIsDisplayed()
  }

  @Test
  fun dashboard_showsSuccessfulModelTestState() = runComposeUiTest {
    setContent {
      DashboardScreen(
          state =
              runningState.copy(
                  modelTestPassed = true,
                  modelTestMessage = "Model loaded and responded successfully.",
              ),
          onStartServer = {},
          onStopServer = {},
          onRunModelTest = {},
          onUnloadModel = {},
          onNavigateToSessionManager = {},
          onNavigateToModelManager = {},
          onNavigateToSettings = {},
      )
    }

    onNodeWithText("Model loaded and responded successfully.").assertIsDisplayed()
  }

  @Test
  fun dashboard_showsModelTestRunningState() = runComposeUiTest {
    setContent {
      DashboardScreen(
          state =
              runningState.copy(
                  isModelTestRunning = true,
                  modelTestMessage = "Testing model...",
              ),
          onStartServer = {},
          onStopServer = {},
          onRunModelTest = {},
          onUnloadModel = {},
          onNavigateToSessionManager = {},
          onNavigateToModelManager = {},
          onNavigateToSettings = {},
      )
    }

    onNodeWithText("Testing model...").assertIsDisplayed()
  }

  @Test
  fun dashboard_showsManageModelsCtaWhenActionNeeded() = runComposeUiTest {
    setContent {
      DashboardScreen(
          state =
              runningState.copy(
                  modelManagement =
                      DashboardModelManagementState(
                          actionNeeded = true,
                          statusMessage = "Model download required",
                          detailMessage = "This model needs to be downloaded again.",
                      )
              ),
          onStartServer = {},
          onStopServer = {},
          onRunModelTest = {},
          onUnloadModel = {},
          onNavigateToSessionManager = {},
          onNavigateToModelManager = {},
          onNavigateToSettings = {},
      )
    }

    onNodeWithText("Model download required").assertIsDisplayed()
    onNodeWithText("Manage Models").assertIsDisplayed()
  }

  @Test
  fun dashboard_hidesManageModelsCtaWhenNoActionNeeded() = runComposeUiTest {
    setContent {
      DashboardScreen(
          state = runningState,
          onStartServer = {},
          onStopServer = {},
          onRunModelTest = {},
          onUnloadModel = {},
          onNavigateToSessionManager = {},
          onNavigateToModelManager = {},
          onNavigateToSettings = {},
      )
    }

    onAllNodesWithText("Manage Models").assertCountEquals(0)
  }
}
