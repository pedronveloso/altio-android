/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
}

apply(from = rootProject.file("gradle/android-library.gradle"))

apply(from = rootProject.file("gradle/android-compose.gradle"))

apply(from = rootProject.file("gradle/junit5.gradle"))

android { namespace = "app.altio.service.ui" }

dependencies {
  implementation(project(":core:domain"))
  implementation(project(":core:data"))

  implementation(libs.androidx.activity.compose)

  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.graphics)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.material3)
  implementation(libs.compose.material.icons.extended)
  implementation(libs.kotlinx.coroutines.core)

  lintChecks(libs.slack.compose.lints)

  debugImplementation(libs.compose.ui.tooling)
  debugImplementation(libs.compose.ui.test.manifest)

  testImplementation(libs.junit5.api)
  testRuntimeOnly(libs.junit5.engine)
  testRuntimeOnly(libs.junit5.launcher)

  androidTestImplementation(platform(libs.compose.bom))
  androidTestImplementation(libs.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
}
