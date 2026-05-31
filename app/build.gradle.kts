/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.metro)
}

apply(from = rootProject.file("gradle/android-application.gradle"))

apply(from = rootProject.file("gradle/android-compose.gradle"))

apply(from = rootProject.file("gradle/junit5.gradle"))

android {
  namespace = "app.altio.service"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "app.altio.service"
    versionCode = 1
    versionName = "1.0.0"
    buildConfigField("boolean", "ALLOW_DIRECT_BATTERY_EXEMPTION_REQUEST", "false")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
          getDefaultProguardFile("proguard-android-optimize.txt"),
          "proguard-rules.pro",
      )
    }
  }

  buildFeatures { buildConfig = true }
}

dependencies {
  implementation(project(":altio-android-sdk"))
  implementation(project(":server"))
  implementation(project(":core:domain"))
  implementation(project(":core:data"))
  implementation(project(":logging"))
  implementation(project(":runtime:litert"))
  implementation(project(":ui"))

  implementation(libs.room.runtime)
  implementation(libs.work.runtime.ktx)
  implementation(libs.okhttp)
  implementation(libs.datastore.preferences)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.activity.compose)
  implementation(libs.material)

  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.graphics)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.material3)
  implementation(libs.compose.material.icons.extended)

  implementation(libs.metro.android)
  implementation(libs.timber)

  lintChecks(libs.slack.compose.lints)

  debugImplementation(libs.compose.ui.tooling)

  testImplementation(libs.junit5.api)
  testRuntimeOnly(libs.junit5.engine)
  testRuntimeOnly(libs.junit5.launcher)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockk)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.compose.bom))
  androidTestImplementation(libs.compose.ui.test.junit4)
  debugImplementation(libs.compose.ui.test.manifest)
}
