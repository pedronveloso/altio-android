/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server.plugins

import io.ktor.server.application.*
import io.ktor.server.request.*
import timber.log.Timber

/**
 * Logs every inbound request and its outcome using Timber.
 * - Inbound: `→ METHOD /path` at INFO
 * - Success (2xx/3xx): `← STATUS METHOD /path (Xms)` at INFO
 * - Client/server error (4xx/5xx): `← STATUS METHOD /path (Xms)` at WARN
 */
fun Application.configureCallLogging() {
  intercept(ApplicationCallPipeline.Monitoring) {
    val start = System.currentTimeMillis()
    val method = call.request.httpMethod.value
    val path = call.request.uri
    Timber.i("\u2192 %s %s", method, path)
    proceed()
    val elapsed = System.currentTimeMillis() - start
    val code = call.response.status()?.value ?: 0
    if (code >= 400) {
      Timber.w("\u2190 %d %s %s (%dms)", code, method, path, elapsed)
    } else {
      Timber.i("\u2190 %d %s %s (%dms)", code, method, path, elapsed)
    }
  }
}
