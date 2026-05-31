/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.runtime

import app.altio.service.domain.model.Model
import app.altio.service.domain.runtime.RuntimeConfig
import app.altio.service.domain.runtime.RuntimeEngine
import app.altio.service.domain.runtime.RuntimeProvider

class RoutingRuntimeProvider(
    private val providers: List<RuntimeProvider>,
) : RuntimeProvider {

  override fun supports(model: Model): Boolean = providers.any { it.supports(model) }

  override suspend fun load(model: Model, config: RuntimeConfig): RuntimeEngine =
      providers.firstOrNull { it.supports(model) }?.load(model, config)
          ?: error("No runtime provider registered for model ${model.definition.id}")
}
