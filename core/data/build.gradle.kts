/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.ksp)
}

apply(from = rootProject.file("gradle/android-library.gradle"))

apply(from = rootProject.file("gradle/junit5.gradle"))

android {
  namespace = "app.altio.service.data"

  defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
}

dependencies {
  implementation(project(":core:domain"))

  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  ksp(libs.room.compiler)

  implementation(libs.datastore.preferences)
  implementation(libs.work.runtime.ktx)
  implementation(libs.okhttp)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.timber)

  testImplementation(libs.junit5.api)
  testRuntimeOnly(libs.junit5.engine)
  testRuntimeOnly(libs.junit5.launcher)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.mockk)

  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.room.testing)
  androidTestImplementation(libs.work.testing)
  androidTestImplementation(libs.okhttp.mockwebserver)
}
