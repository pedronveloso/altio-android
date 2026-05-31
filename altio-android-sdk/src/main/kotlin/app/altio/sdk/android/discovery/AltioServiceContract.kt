/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.android.discovery

import android.net.Uri
import androidx.core.net.toUri

object AltioServiceContract {
  const val portProviderAuthority = "app.altio.service.port"
  const val portColumn = "port"
  const val actionPortChanged = "app.altio.service.PORT_CHANGED"
  const val extraPort = "port"

  val portContentUri: Uri = "content://$portProviderAuthority/port".toUri()
}
