/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.power

import android.content.Intent

enum class OemFamily {
  UNKNOWN,
  XIAOMI,
  REDMI,
  POCO,
}

data class OemGuidanceStep(
    val title: String,
    val detail: String,
)

data class OemGuidance(
    val family: OemFamily,
    val deviceLabel: String,
    val title: String,
    val summary: String,
    val steps: List<OemGuidanceStep>,
    val settingsIntents: List<Intent>,
)
