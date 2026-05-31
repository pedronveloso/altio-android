/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.runtime.demo

import app.altio.service.domain.model.Model
import app.altio.service.domain.runtime.RuntimeConfig
import app.altio.service.domain.runtime.RuntimeEngine
import app.altio.service.domain.runtime.RuntimeProvider
import kotlin.random.Random

class DemoRuntimeProvider(
    private val random: Random = Random.Default,
) : RuntimeProvider {

  override fun supports(model: Model): Boolean = model.definition.runtime == "demo"

  override suspend fun load(model: Model, config: RuntimeConfig): RuntimeEngine =
      DemoRuntimeEngine(random)
}
