/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import app.altio.sdk.android.discovery.AltioServiceContract
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Exposes the current server port to other apps on the device.
 *
 * Query URI: `content://app.altio.service.port/port` Returns a single-row cursor with column `port`
 * (Int) or -1 if the server is not running.
 */
class PortContentProvider : ContentProvider() {

  override fun onCreate(): Boolean = true

  override fun query(
      uri: Uri,
      projection: Array<out String>?,
      selection: String?,
      selectionArgs: Array<out String>?,
      sortOrder: String?,
  ): Cursor? {
    if (MATCHER.match(uri) != PORT_CODE) return null
    val appGraph = (context!!.applicationContext as AiServiceApplication).appGraph
    val port = runBlocking { appGraph.server.port.first() } ?: -1
    return MatrixCursor(arrayOf(COLUMN_PORT)).apply { addRow(arrayOf(port)) }
  }

  override fun getType(uri: Uri): String? = null

  override fun insert(uri: Uri, values: ContentValues?): Uri? = null

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

  override fun update(
      uri: Uri,
      values: ContentValues?,
      selection: String?,
      selectionArgs: Array<out String>?,
  ): Int = 0

  companion object {
    private const val PORT_CODE = 1
    const val COLUMN_PORT = AltioServiceContract.portColumn

    val CONTENT_URI: Uri = AltioServiceContract.portContentUri

    private val MATCHER =
        UriMatcher(UriMatcher.NO_MATCH).apply {
          addURI(AltioServiceContract.portProviderAuthority, "port", PORT_CODE)
        }
  }
}
