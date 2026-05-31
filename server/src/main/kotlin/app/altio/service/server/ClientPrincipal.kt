/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server

import io.ktor.server.auth.Principal

/** Represents an authenticated client identified by a valid bearer token. */
data class ClientPrincipal(val clientId: String, val label: String) : Principal
