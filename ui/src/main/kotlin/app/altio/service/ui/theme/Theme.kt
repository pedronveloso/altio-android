/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun AiServiceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val colorScheme =
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

  MaterialTheme(
      colorScheme = colorScheme,
      content = content,
  )
}
