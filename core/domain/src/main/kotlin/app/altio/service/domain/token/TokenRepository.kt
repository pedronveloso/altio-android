/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.token

import kotlinx.coroutines.flow.Flow

interface TokenRepository {
  /**
   * Generates a cryptographically secure bearer token, stores its SHA-256 hash, and returns the raw
   * token string. The raw token is returned exactly once — callers must present it to the user
   * immediately. After this call the raw token cannot be recovered.
   */
  suspend fun generateToken(label: String): String

  /**
   * Validates a raw bearer token. Returns the associated [ClientToken] if the token exists and is
   * not revoked, and records the access time. Returns null otherwise.
   */
  suspend fun validateToken(rawToken: String): ClientToken?

  /**
   * Inspects a raw bearer token for diagnostics. This is intended for auth logging and should not
   * be used to expose token material.
   */
  suspend fun inspectToken(rawToken: String): TokenInspectionResult =
      when (val token = validateToken(rawToken)) {
        null -> TokenInspectionResult.Unknown
        else -> TokenInspectionResult.Valid(token)
      }

  /** Marks the token identified by [tokenHash] as revoked. */
  suspend fun revokeToken(tokenHash: String)

  /** Emits the list of active (non-revoked) tokens, re-emitting on any change. */
  fun observeTokens(): Flow<List<ClientToken>>
}
