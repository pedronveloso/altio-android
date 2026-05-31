/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.token

sealed interface TokenInspectionResult {
  data class Valid(val token: ClientToken) : TokenInspectionResult

  data class Revoked(val token: ClientToken) : TokenInspectionResult

  data object Unknown : TokenInspectionResult
}
