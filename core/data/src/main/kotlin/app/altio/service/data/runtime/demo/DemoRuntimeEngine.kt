/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.runtime.demo

import app.altio.service.domain.runtime.RuntimeEngine
import app.altio.service.domain.runtime.RuntimeSession
import app.altio.service.domain.runtime.SessionParams
import kotlin.random.Random

class DemoRuntimeEngine(
    private val random: Random,
) : RuntimeEngine {

  override suspend fun createSession(sessionId: String, params: SessionParams): RuntimeSession =
      DemoRuntimeSession(
          sessionId = sessionId,
          params = params,
          random = random,
      )

  override fun close() = Unit
}
