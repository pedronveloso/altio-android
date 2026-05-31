/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server.routes

import app.altio.sdk.contract.health.HealthResponse
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.healthRoute() {
  get("/v1/health") { call.respond(HealthResponse(status = "ok")) }
}
