/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.serialization)
}

apply(from = rootProject.file("gradle/android-library.gradle"))

apply(from = rootProject.file("gradle/junit5.gradle"))

android { namespace = "app.altio.service.server" }

dependencies {
  implementation(project(":altio-android-sdk"))
  implementation(project(":core:domain"))
  implementation(project(":core:data"))

  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.cio)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.server.sse)
  implementation(libs.ktor.server.auth)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.timber)

  testImplementation(libs.ktor.server.test.host)
  testImplementation(libs.junit5.api)
  testRuntimeOnly(libs.junit5.engine)
  testRuntimeOnly(libs.junit5.launcher)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockk)
}
