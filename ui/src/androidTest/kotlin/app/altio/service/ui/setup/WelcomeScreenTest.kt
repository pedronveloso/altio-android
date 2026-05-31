/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.ui.setup

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class WelcomeScreenTest {

  @Test
  fun welcomeScreen_showsAppNameAndButton() = runComposeUiTest {
    setContent { WelcomeScreen(onGetStarted = {}, versionName = "1.0.0") }

    onNodeWithText("Altio Service").assertIsDisplayed()
    onNodeWithText("Get Started").assertIsDisplayed()
  }

  @Test
  fun welcomeScreen_getStartedButton_invokesCallback() = runComposeUiTest {
    var clicked = false
    setContent { WelcomeScreen(onGetStarted = { clicked = true }, versionName = "1.0.0") }

    onNodeWithText("Get Started").performClick()
    assertTrue(clicked)
  }
}
