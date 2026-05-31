/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "Altio Service"

include(":app")

include(":core:domain")

include(":core:data")

include(":server")

include(":runtime:litert")

include(":ui")

include(":demo")

include(":altio-android-sdk")

include(":logging")
