/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import app.altio.sdk.android.discovery.AltioServiceContract

/**
 * Copies the service port number to the clipboard when the user taps the notification action.
 * Registered dynamically by [AiBackgroundService] — no manifest entry required.
 */
class CopyPortReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val port = intent.getIntExtra(EXTRA_PORT, -1)
    if (port == -1) return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("port", port.toString()))
    Toast.makeText(context, "Port $port copied", Toast.LENGTH_SHORT).show()
  }

  companion object {
    const val ACTION_COPY_PORT = "app.altio.service.COPY_PORT"
    const val EXTRA_PORT = AltioServiceContract.extraPort
  }
}
