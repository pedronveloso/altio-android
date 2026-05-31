/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.android.discovery

import android.content.Context
import android.content.IntentFilter

object PortDiscovery {
  fun resolvePort(context: Context): Int? =
      context.contentResolver
          .query(AltioServiceContract.portContentUri, null, null, null, null)
          ?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getInt(cursor.getColumnIndexOrThrow(AltioServiceContract.portColumn)).takeIf {
              it > 0
            }
          }

  fun portChangedIntentFilter(): IntentFilter = IntentFilter(AltioServiceContract.actionPortChanged)
}
