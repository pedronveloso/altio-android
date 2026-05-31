/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.ui.device

data class DeviceOemGuidanceUi(
    val title: String,
    val summary: String,
    val steps: List<String>,
    val actionLabel: String = "Open Device Settings",
)
