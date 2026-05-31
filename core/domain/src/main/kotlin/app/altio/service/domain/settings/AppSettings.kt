/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.settings

data class AppSettings(
    val setupComplete: Boolean = false,
    val onboardingCheckpoint: OnboardingCheckpoint = OnboardingCheckpoint.WELCOME,
    val batteryOptimizationGuidanceSeen: Boolean = false,
    val oemGuidanceSeen: Boolean = false,
    val activeModelId: String = "demo-model",
    val idleShutdownMinutes: Int? = 10, // null means Never
    val startOnBoot: Boolean = false,
    val serverPort: Int = DEFAULT_SERVER_PORT,
    val accelerator: String = "Auto", // "Auto", "CPU", "GPU"
    val modelAccelerators: Map<String, String> = emptyMap(), // modelId -> "CPU" | "GPU"
    val maxTokens: Int = 2048,
) {
  companion object {
    const val DEFAULT_SERVER_PORT = 52731
    const val MIN_SERVER_PORT = 1024
    const val MAX_SERVER_PORT = 65535
  }
}
