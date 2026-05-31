/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server.routes

import app.altio.sdk.contract.health.DiagnosticsResponse
import app.altio.service.domain.job.JobRepository
import app.altio.service.domain.session.SessionRepository
import app.altio.service.server.ClientPrincipal
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant

fun Route.diagnosticsRoute(
    sessionRepository: SessionRepository,
    jobRepository: JobRepository,
    serverStartedAt: Instant,
) {
  get("/v1/diagnostics") {
    val caller = call.principal<ClientPrincipal>()!!
    val activeSessions = sessionRepository.countByClient(caller.clientId)
    val activeJobs = jobRepository.countActiveByClient(caller.clientId)
    val uptimeSeconds = Instant.now().epochSecond - serverStartedAt.epochSecond
    call.respond(
        DiagnosticsResponse(
            uptimeSeconds = uptimeSeconds,
            activeSessions = activeSessions,
            activeJobs = activeJobs,
        )
    )
  }
}
