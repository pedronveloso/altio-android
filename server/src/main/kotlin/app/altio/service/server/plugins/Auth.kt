/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server.plugins

import app.altio.service.domain.token.TokenInspectionResult
import app.altio.service.domain.token.TokenRepository
import app.altio.service.server.ClientPrincipal
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import java.security.MessageDigest
import timber.log.Timber

const val TOKEN_AUTH = "token-auth"

fun Application.configureAuth(tokenRepository: TokenRepository) {
  intercept(ApplicationCallPipeline.Plugins) {
    if (call.request.path() == "/v1/health") return@intercept
    val authorization = call.request.headers[HttpHeaders.Authorization]
    when {
      authorization == null ->
          Timber.w(
              "Auth missing Authorization header for %s %s",
              call.request.httpMethod.value,
              call.request.uri,
          )
      !authorization.startsWith("Bearer ") ->
          Timber.w(
              "Auth malformed Authorization header for %s %s scheme=%s",
              call.request.httpMethod.value,
              call.request.uri,
              authorization.substringBefore(' '),
          )
      authorization.removePrefix("Bearer ").trim().isEmpty() ->
          Timber.w(
              "Auth empty bearer token for %s %s",
              call.request.httpMethod.value,
              call.request.uri,
          )
    }
  }

  install(Authentication) {
    bearer(TOKEN_AUTH) {
      authenticate { credential ->
        val tokenFingerprint = credential.token.tokenFingerprint()
        val tokenLength = credential.token.length
        when (val result = tokenRepository.inspectToken(credential.token)) {
          is TokenInspectionResult.Valid -> {
            Timber.i(
                "Auth accepted token clientId=%s label=%s tokenLen=%d tokenSha=%s",
                result.token.clientId,
                result.token.label,
                tokenLength,
                tokenFingerprint,
            )
            ClientPrincipal(clientId = result.token.clientId, label = result.token.label)
          }
          is TokenInspectionResult.Revoked -> {
            Timber.w(
                "Auth rejected revoked token clientId=%s label=%s tokenLen=%d tokenSha=%s",
                result.token.clientId,
                result.token.label,
                tokenLength,
                tokenFingerprint,
            )
            null
          }
          TokenInspectionResult.Unknown -> {
            Timber.w(
                "Auth rejected unknown token tokenLen=%d tokenSha=%s",
                tokenLength,
                tokenFingerprint,
            )
            null
          }
        }
      }
    }
  }
}

private fun String.tokenFingerprint(): String {
  val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
  return digest.joinToString("") { "%02x".format(it) }.take(12)
}
