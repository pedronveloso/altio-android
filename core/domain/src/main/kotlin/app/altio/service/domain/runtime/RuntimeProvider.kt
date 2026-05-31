/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.runtime

import app.altio.service.domain.model.Model

/** Entry point for a model backend. Implement this to add a new inference engine. */
interface RuntimeProvider {
  /** Returns true if this provider can handle the given model file. */
  fun supports(model: Model): Boolean

  /** Loads the given [model] and returns a ready [RuntimeEngine]. */
  suspend fun load(model: Model, config: RuntimeConfig): RuntimeEngine
}
