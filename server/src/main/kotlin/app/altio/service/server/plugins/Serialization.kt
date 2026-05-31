/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server.plugins

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.sse.*
import kotlinx.serialization.json.Json

fun Application.configureSerialization() {
  install(SSE)
  install(ContentNegotiation) {
    json(
        Json {
          ignoreUnknownKeys = true
          encodeDefaults = true
          explicitNulls = false
        }
    )
  }
}
