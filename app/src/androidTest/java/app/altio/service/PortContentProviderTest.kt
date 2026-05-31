/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.altio.sdk.android.discovery.AltioServiceContract
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortContentProviderTest {

  @Test
  fun query_returnsActivePortFromAppGraph() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val appGraph = (context.applicationContext as AiServiceApplication).appGraph

    val expectedPort = runBlocking { appGraph.server.port.first() } ?: -1

    val uri = Uri.parse("content://app.altio.service.port/port")
    val cursor = context.contentResolver.query(uri, null, null, null, null)

    assertNotNull("Cursor should not be null", cursor)
    cursor!!.use { c ->
      assertTrue("Cursor should have at least one row", c.moveToFirst())
      val portIndex = c.getColumnIndex(AltioServiceContract.portColumn)
      assertTrue("Cursor should contain port column", portIndex >= 0)
      val actualPort = c.getInt(portIndex)
      assertEquals(expectedPort, actualPort)
    }
  }

  @Test
  fun query_returnsNull_forInvalidUri() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val uri = Uri.parse("content://app.altio.service.port/invalid_path")
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    assertEquals(null, cursor)
  }
}
