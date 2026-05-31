/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.contract.error

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ApiErrorCode {
  @SerialName("UNAUTHORIZED") UNAUTHORIZED,
  @SerialName("SESSION_NOT_FOUND") SESSION_NOT_FOUND,
  @SerialName("JOB_NOT_FOUND") JOB_NOT_FOUND,
  @SerialName("MODEL_NOT_FOUND") MODEL_NOT_FOUND,
  @SerialName("MODEL_NOT_LOADED") MODEL_NOT_LOADED,
  @SerialName("MODEL_NOT_READY") MODEL_NOT_READY,
  @SerialName("MODEL_IN_USE") MODEL_IN_USE,
  @SerialName("REQUEST_TOO_LARGE") REQUEST_TOO_LARGE,
  @SerialName("INVALID_INPUT") INVALID_INPUT,
  @SerialName("INFERENCE_FAILED") INFERENCE_FAILED,
  @SerialName("RATE_LIMITED") RATE_LIMITED,
  @SerialName("CANCELLED") CANCELLED,
  @SerialName("UNSUPPORTED_FORMAT") UNSUPPORTED_FORMAT,
  @SerialName("NOT_IMPLEMENTED") NOT_IMPLEMENTED,
  @SerialName("INTERNAL_ERROR") INTERNAL_ERROR,
}
