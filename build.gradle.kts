/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.metro) apply false
  alias(libs.plugins.spotless)
}

spotless {
  kotlin {
    target("**/*.kt")
    targetExclude("**/build/**")
    licenseHeader(
        """
        /*
         * SPDX-License-Identifier: AGPL-3.0-or-later
         */
        """
            .trimIndent(),
    )
    ktfmt()
    trimTrailingWhitespace()
    endWithNewline()
  }

  kotlinGradle {
    target("**/*.gradle.kts")
    targetExclude("**/build/**")
    licenseHeader(
        """
        /*
         * SPDX-License-Identifier: AGPL-3.0-or-later
         */
        """
            .trimIndent(),
        "^(?!\\s*(\\/\\*|\\*|\\/\\/))\\s*\\S",
    )
    ktfmt()
    trimTrailingWhitespace()
    endWithNewline()
  }

  groovyGradle {
    target("**/*.gradle")
    targetExclude("**/build/**")
    licenseHeader(
        """
        /*
         * SPDX-License-Identifier: AGPL-3.0-or-later
         */
        """
            .trimIndent(),
        "^(?!\\s*(\\/\\*|\\*|\\/\\/))\\s*\\S",
    )
  }

  format("xml") {
    target("**/*.xml")
    targetExclude("**/build/**")
    licenseHeader(
            """
            <!--
              SPDX-License-Identifier: AGPL-3.0-or-later
            -->
            """
                .trimIndent(),
            "(<[^!?])",
        )
        .skipLinesMatching("^<\\?xml.*\\?>$")
  }

  format("misc") {
    target("**/*.md", "**/.gitignore")
    trimTrailingWhitespace()
    leadingTabsToSpaces(2)
  }
}
