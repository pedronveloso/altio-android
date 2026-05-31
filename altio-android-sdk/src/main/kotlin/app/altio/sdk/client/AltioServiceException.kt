/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.client

import app.altio.sdk.contract.error.ApiError
import app.altio.sdk.contract.error.ApiErrorCode

sealed class AltioServiceException(
    message: String,
    val statusCode: Int? = null,
    val apiError: ApiError? = null,
) : Exception(message)

class UnauthorizedException(message: String, apiError: ApiError? = null) :
    AltioServiceException(message = message, statusCode = 401, apiError = apiError)

class NotFoundException(message: String, apiError: ApiError) :
    AltioServiceException(message = message, statusCode = 404, apiError = apiError)

class ConflictException(message: String, apiError: ApiError) :
    AltioServiceException(message = message, statusCode = 409, apiError = apiError)

class RateLimitedException(message: String, apiError: ApiError) :
    AltioServiceException(message = message, statusCode = 429, apiError = apiError)

class InvalidInputException(message: String, apiError: ApiError) :
    AltioServiceException(message = message, statusCode = 400, apiError = apiError)

class RequestTooLargeException(message: String, apiError: ApiError) :
    AltioServiceException(message = message, statusCode = 413, apiError = apiError)

class UnsupportedFormatException(message: String, apiError: ApiError) :
    AltioServiceException(message = message, statusCode = 415, apiError = apiError)

class NotImplementedException(message: String, apiError: ApiError) :
    AltioServiceException(message = message, statusCode = 501, apiError = apiError)

class ServiceUnavailableException(message: String, apiError: ApiError? = null) :
    AltioServiceException(message = message, statusCode = 503, apiError = apiError)

class HttpFailureException(message: String, statusCode: Int, apiError: ApiError? = null) :
    AltioServiceException(message = message, statusCode = statusCode, apiError = apiError)

internal fun createAltioServiceException(
    statusCode: Int,
    apiError: ApiError?,
): AltioServiceException {
  val defaultMessage = apiError?.message ?: "Request failed with HTTP $statusCode"
  return when {
    statusCode == 401 ->
        UnauthorizedException(
            message = defaultMessage,
            apiError = apiError ?: ApiError(ApiErrorCode.UNAUTHORIZED, defaultMessage),
        )
    statusCode == 404 && apiError != null -> NotFoundException(defaultMessage, apiError)
    statusCode == 409 && apiError != null -> ConflictException(defaultMessage, apiError)
    statusCode == 429 && apiError != null -> RateLimitedException(defaultMessage, apiError)
    statusCode == 400 && apiError != null -> InvalidInputException(defaultMessage, apiError)
    statusCode == 413 && apiError != null -> RequestTooLargeException(defaultMessage, apiError)
    statusCode == 415 && apiError != null -> UnsupportedFormatException(defaultMessage, apiError)
    statusCode == 501 && apiError != null -> NotImplementedException(defaultMessage, apiError)
    statusCode == 503 -> ServiceUnavailableException(defaultMessage, apiError)
    else -> HttpFailureException(defaultMessage, statusCode, apiError)
  }
}
