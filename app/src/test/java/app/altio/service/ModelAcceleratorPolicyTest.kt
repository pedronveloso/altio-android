/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service

import app.altio.service.domain.model.Model
import app.altio.service.domain.model.ModelCapability
import app.altio.service.domain.model.ModelDefaultConfig
import app.altio.service.domain.model.ModelDefinition
import app.altio.service.domain.model.ModelFile
import app.altio.service.domain.model.ModelSource
import app.altio.service.domain.model.ModelStatus
import app.altio.service.domain.runtime.Accelerator
import app.altio.service.domain.settings.AppSettings
import app.altio.service.domain.settings.isOpenClAvailabilityFailure
import app.altio.service.domain.settings.resolveAccelerator
import app.altio.service.domain.settings.shouldLearnCpuForModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelAcceleratorPolicyTest {

  @Test
  fun `auto uses learned cpu accelerator for matching model`() {
    val settings =
        AppSettings(
            accelerator = "Auto",
            modelAccelerators = mapOf("gemma-4-e2b-it" to "CPU"),
        )

    assertEquals(Accelerator.CPU, settings.resolveAccelerator(gemmaModel()))
  }

  @Test
  fun `auto falls back to model gpu default when no learned accelerator exists`() {
    val settings = AppSettings(accelerator = "Auto")

    assertEquals(Accelerator.GPU, settings.resolveAccelerator(gemmaModel()))
  }

  @Test
  fun `explicit accelerator overrides learned per-model preference`() {
    val settings =
        AppSettings(
            accelerator = "GPU",
            modelAccelerators = mapOf("gemma-4-e2b-it" to "CPU"),
        )

    assertEquals(Accelerator.GPU, settings.resolveAccelerator(gemmaModel()))
  }

  @Test
  fun `opencl failure is detected through nested cause chain`() {
    val error =
        IllegalStateException(
            "outer",
            RuntimeException("Can not find OpenCL library on this device"),
        )

    assertTrue(error.isOpenClAvailabilityFailure())
  }

  @Test
  fun `litertlm jni exception is detected as opencl availability failure`() {
    class FakeLiteRtLmJniException(message: String) : Exception(message)

    val error =
        IllegalStateException(
            "outer",
            FakeLiteRtLmJniException("Failed to create engine: INTERNAL: ERROR"),
        )
    assertTrue(error.isOpenClAvailabilityFailure())
  }

  @Test
  fun `unimplemented create shared memory manager is detected`() {
    val error = RuntimeException("CreateSharedMemoryManager is not implemented")
    assertTrue(error.isOpenClAvailabilityFailure())
  }

  @Test
  fun `shouldLearnCpuForModel only when user is on auto and cpu is not already learned`() {
    assertTrue(AppSettings(accelerator = "Auto").shouldLearnCpuForModel("gemma-4-e2b-it"))
    assertFalse(
        AppSettings(accelerator = "CPU", modelAccelerators = mapOf("gemma-4-e2b-it" to "GPU"))
            .shouldLearnCpuForModel("gemma-4-e2b-it")
    )
    assertFalse(
        AppSettings(accelerator = "Auto", modelAccelerators = mapOf("gemma-4-e2b-it" to "CPU"))
            .shouldLearnCpuForModel("gemma-4-e2b-it")
    )
  }
}

private fun gemmaModel(): Model =
    Model(
        definition =
            ModelDefinition(
                id = "gemma-4-e2b-it",
                name = "Gemma 4 E2B IT",
                description = "test",
                source = ModelSource.DOWNLOADED,
                version = "1",
                huggingfaceRepo = "repo",
                sizeBytes = 1L,
                sha256 = "hash",
                capabilities = listOf(ModelCapability.TEXT),
                minSdk = 31,
                minDeviceMemoryGb = 8,
                maxContextLength = 2048,
                runtime = "litert-lm",
                files = listOf(ModelFile("model.bin", 1L, "hash")),
                defaultConfig =
                    ModelDefaultConfig(
                        topK = 1,
                        topP = 1f,
                        temperature = 1f,
                        maxTokens = 128,
                        accelerators = listOf("gpu", "cpu"),
                    ),
            ),
        status = ModelStatus.READY,
        filePath = "/tmp/model.bin",
        downloadedAt = null,
    )
