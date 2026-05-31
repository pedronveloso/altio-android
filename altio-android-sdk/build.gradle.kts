/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.serialization)
}

apply(from = rootProject.file("gradle/android-library.gradle"))

apply(from = rootProject.file("gradle/junit5.gradle"))

android { namespace = "app.altio.sdk" }

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)

  testImplementation(libs.junit5.api)
  testRuntimeOnly(libs.junit5.engine)
  testRuntimeOnly(libs.junit5.launcher)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.turbine)
}
