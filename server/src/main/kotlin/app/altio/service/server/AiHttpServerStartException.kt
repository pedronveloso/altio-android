/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server

class AiHttpServerStartException(
    val kind: Kind,
    val attemptedPort: Int,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
  enum class Kind {
    PORT_IN_USE,
    INVALID_PORT,
    BIND_FAILED,
    ENGINE_FAILED,
  }
}
