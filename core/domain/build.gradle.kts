/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
plugins { alias(libs.plugins.kotlin.jvm) }

apply(from = rootProject.file("gradle/kotlin-jvm.gradle"))

apply(from = rootProject.file("gradle/junit5.gradle"))

dependencies {
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.junit5.api)
  testRuntimeOnly(libs.junit5.engine)
  testRuntimeOnly(libs.junit5.launcher)
}
