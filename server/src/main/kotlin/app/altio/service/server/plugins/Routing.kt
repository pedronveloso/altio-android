/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server.plugins

import app.altio.service.data.session.SessionCoordinator
import app.altio.service.server.ServerDependencies
import app.altio.service.server.routes.diagnosticsRoute
import app.altio.service.server.routes.generateRoute
import app.altio.service.server.routes.healthRoute
import app.altio.service.server.routes.jobsRoute
import app.altio.service.server.routes.modelsRoute
import app.altio.service.server.routes.sessionsRoute
import app.altio.service.server.routes.transcribeRoute
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import java.time.Instant

fun Application.configureRouting(
    deps: ServerDependencies,
    serverStartedAt: Instant = Instant.now(),
) {
  val sessionCoordinator = SessionCoordinator(deps.sessionRepository, deps.sessionManager)
  routing {
    // Health is unauthenticated — used for connectivity checks before token setup.
    healthRoute()

    // All other endpoints require a valid bearer token.
    authenticate(TOKEN_AUTH) {
      modelsRoute(deps.modelRepository, deps.engineHolder)
      sessionsRoute(
          deps.modelRepository,
          deps.settingsRepository,
          deps.sessionRepository,
          sessionCoordinator,
      )
      generateRoute(
          deps.sessionRepository,
          sessionCoordinator,
          deps.jobRepository,
          deps.inferenceScheduler,
      )
      transcribeRoute(
          deps.modelRepository,
          deps.sessionRepository,
          sessionCoordinator,
          deps.jobRepository,
          deps.inferenceScheduler,
      )
      jobsRoute(deps.jobRepository, deps.inferenceScheduler)
      diagnosticsRoute(deps.sessionRepository, deps.jobRepository, serverStartedAt)
    }
  }
}
