/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service

import android.provider.Settings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NotificationPermissionHelperTest {

  @Test
  fun appNotificationSettingsIntentSpec_targetsAppNotificationSettingsWithPackageExtra() {
    val spec = NotificationPermissionHelper.appNotificationSettingsIntentSpec(PACKAGE_NAME)

    assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, spec.action)
    assertEquals(Settings.EXTRA_APP_PACKAGE, spec.packageExtraKey)
    assertEquals(PACKAGE_NAME, spec.packageExtraValue)
    assertNull(spec.dataScheme)
    assertNull(spec.dataSchemeSpecificPart)
  }

  @Test
  fun appDetailsSettingsIntentSpec_targetsAppDetailsWithPackageUri() {
    val spec = NotificationPermissionHelper.appDetailsSettingsIntentSpec(PACKAGE_NAME)

    assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, spec.action)
    assertNull(spec.packageExtraKey)
    assertNull(spec.packageExtraValue)
    assertEquals("package", spec.dataScheme)
    assertEquals(PACKAGE_NAME, spec.dataSchemeSpecificPart)
  }

  private companion object {
    const val PACKAGE_NAME = "app.altio.service"
  }
}
