/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.runtime.litert

import app.altio.service.domain.runtime.RuntimeEngine
import app.altio.service.domain.runtime.RuntimeSession
import app.altio.service.domain.runtime.SessionParams
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.SamplerConfig

class LiteRtRuntimeEngine(private val engine: Engine) : RuntimeEngine {

  override suspend fun createSession(sessionId: String, params: SessionParams): RuntimeSession {
    val samplerConfig =
        SamplerConfig(
            topK = params.generationConfig.topK,
            topP = params.generationConfig.topP.toDouble(),
            temperature = params.generationConfig.temperature.toDouble(),
        )
    val config =
        ConversationConfig(
            systemInstruction = params.systemPrompt?.let { Contents.of(it) },
            samplerConfig = samplerConfig,
        )
    val conversation = engine.createConversation(config)
    return LiteRtRuntimeSession(
        sessionId = sessionId,
        conversation = conversation,
        params = params,
        engine = engine,
    )
  }

  override fun close() {
    engine.close()
  }
}
