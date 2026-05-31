/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
}

apply(from = rootProject.file("gradle/android-application.gradle"))

apply(from = rootProject.file("gradle/android-compose.gradle"))

android {
  namespace = "app.altio.service.demo"

  defaultConfig {
    applicationId = "app.altio.service.demo"
    versionCode = 1
    versionName = "Beta 1"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  buildFeatures { buildConfig = true }
}

dependencies {
  implementation(project(":altio-android-sdk"))
  implementation(project(":logging"))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.activity.compose)

  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.material3)

  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.timber)

  lintChecks(libs.slack.compose.lints)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  debugImplementation(libs.compose.ui.tooling)
}
