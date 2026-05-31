/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
plugins { alias(libs.plugins.android.library) }

apply(from = rootProject.file("gradle/android-library.gradle"))

apply(from = rootProject.file("gradle/junit5.gradle"))

android { namespace = "app.altio.service.runtime.litert" }

dependencies {
  implementation(project(":core:domain"))

  implementation(libs.litertlm.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.timber)
}
