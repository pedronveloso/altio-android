/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.contract.error

import kotlinx.serialization.Serializable

@Serializable data class ApiError(val code: ApiErrorCode, val message: String)
