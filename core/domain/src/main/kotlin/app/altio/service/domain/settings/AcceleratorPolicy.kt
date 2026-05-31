/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.settings

import app.altio.service.domain.model.Model
import app.altio.service.domain.runtime.Accelerator

fun AppSettings.resolveAccelerator(model: Model): Accelerator {
  val configured = accelerator.toAccelerator()
  if (configured != Accelerator.AUTO) return configured

  val learned = modelAccelerators[model.definition.id]?.toAccelerator() ?: Accelerator.AUTO
  if (learned != Accelerator.AUTO) return learned

  return when {
    model.definition.defaultConfig.accelerators.any { it.equals("gpu", ignoreCase = true) } ->
        Accelerator.GPU
    else -> Accelerator.CPU
  }
}

fun AppSettings.shouldLearnCpuForModel(modelId: String): Boolean =
    accelerator.toAccelerator() == Accelerator.AUTO && modelAccelerators[modelId] != "CPU"

fun String.toAccelerator(): Accelerator =
    when (uppercase()) {
      "CPU" -> Accelerator.CPU
      "GPU" -> Accelerator.GPU
      "NPU" -> Accelerator.NPU
      else -> Accelerator.AUTO
    }

fun Throwable.isOpenClAvailabilityFailure(): Boolean {
  var current: Throwable? = this
  while (current != null) {
    val message = current.message.orEmpty()
    val className = current.javaClass.name
    if (
        message.contains("OpenCL", ignoreCase = true) ||
            message.contains("libLiteRtTopKOpenClSampler", ignoreCase = true) ||
            message.contains("Failed to create engine", ignoreCase = true) ||
            message.contains("CreateSharedMemoryManager", ignoreCase = true) ||
            className.contains("LiteRtLmJniException", ignoreCase = true) ||
            (message.contains("GPU", ignoreCase = true) &&
                message.contains("not available", ignoreCase = true))
    ) {
      return true
    }
    current = current.cause
  }
  return false
}
