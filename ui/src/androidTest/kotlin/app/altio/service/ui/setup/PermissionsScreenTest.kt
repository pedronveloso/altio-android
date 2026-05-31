/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.ui.setup

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PermissionsScreenTest {

  @Test
  fun permissionGranted_showsGrantedStateAndEnablesContinue() = runComposeUiTest {
    setContent { TestPermissionsScreen(notificationsEnabled = true) }

    onNodeWithText("Granted").assertIsDisplayed()
    onNodeWithText("Continue").assertIsEnabled()
  }

  @Test
  fun permissionDeniedBeforeRequest_showsGrantAndDisablesContinue() = runComposeUiTest {
    setContent {
      TestPermissionsScreen(
          notificationsEnabled = false,
          notificationRequestAttempted = false,
      )
    }

    onNodeWithText("Grant").assertIsDisplayed()
    onNodeWithText("Continue").assertIsNotEnabled()
  }

  @Test
  fun permissionDeniedAfterRequest_showsOpenSettingsAndDisablesContinue() = runComposeUiTest {
    setContent {
      TestPermissionsScreen(
          notificationsEnabled = false,
          notificationRequestAttempted = true,
      )
    }

    onNodeWithText("Open Settings").assertIsDisplayed()
    onNodeWithText("Continue").assertIsNotEnabled()
  }

  @Composable
  private fun TestPermissionsScreen(
      notificationsEnabled: Boolean,
      notificationRequestAttempted: Boolean = false,
  ) {
    PermissionsScreen(
        batteryOptimizationDisabled = true,
        notificationsEnabled = notificationsEnabled,
        notificationRequestAttempted = notificationRequestAttempted,
        supportsDirectBatteryExemption = false,
        oemGuidance = null,
        onRequestNotifications = {},
        onOpenNotificationSettings = {},
        onOpenBatteryOptimizationSettings = {},
        onRequestDirectBatteryExemption = {},
        onOpenOemSettings = {},
        onContinue = {},
    )
  }
}
