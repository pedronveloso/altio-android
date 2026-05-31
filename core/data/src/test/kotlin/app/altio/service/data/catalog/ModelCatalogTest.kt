/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.catalog

import app.altio.service.domain.model.ModelCapability
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelCatalogTest {

  @Test
  fun `catalog contains expected models`() {
    assertEquals(4, ModelCatalog.all.size)
  }

  @Test
  fun `gemma 4 e2b it is findable by id`() {
    assertNotNull(ModelCatalog.find("gemma-4-e2b-it"))
  }

  @Test
  fun `qwen 3_5 2b instruct is findable by id`() {
    assertNotNull(ModelCatalog.find("qwen-3.5-2b-instruct"))
  }

  @Test
  fun `qwen 3_5 0_8b instruct is findable by id`() {
    assertNotNull(ModelCatalog.find("qwen-3.5-0.8b-instruct"))
  }

  @Test
  fun `demo model is findable by id`() {
    assertNotNull(ModelCatalog.find("demo-model"))
  }

  @Test
  fun `find returns null for unknown id`() {
    assertNull(ModelCatalog.find("unknown-model"))
  }

  @Test
  fun `gemma 4 e2b it has required capabilities`() {
    val model = ModelCatalog.gemma4E2bIt
    val capabilities = model.capabilities
    assert(ModelCapability.TEXT in capabilities)
    assert(ModelCapability.AUDIO in capabilities)
    assert(ModelCapability.VISION in capabilities)
    assert(ModelCapability.THINKING in capabilities)
  }

  @Test
  fun `gemma 4 e2b it has exactly one file`() {
    assertEquals(1, ModelCatalog.gemma4E2bIt.files.size)
  }

  @Test
  fun `gemma 4 e2b it file has litertlm extension`() {
    val file = ModelCatalog.gemma4E2bIt.files.first()
    assert(file.name.endsWith(".litertlm")) { "Expected .litertlm extension, got: ${file.name}" }
  }

  @Test
  fun `gemma 4 e2b it requires min 8gb ram`() {
    assertEquals(8, ModelCatalog.gemma4E2bIt.minDeviceMemoryGb)
  }

  @Test
  fun `gemma 4 e2b it min sdk is 31`() {
    assertEquals(31, ModelCatalog.gemma4E2bIt.minSdk)
  }

  @Test
  fun `catalog does not ship placeholder hashes`() {
    assertFalse(ModelCatalog.gemma4E2bIt.sha256.all { it == '0' })
    assertFalse(ModelCatalog.gemma4E2bIt.files.first().sha256.all { it == '0' })
  }

  @Test
  fun `demo model is built in and ready for mock testing`() {
    val model = ModelCatalog.demoModel
    assertEquals("demo", model.runtime)
    assertEquals(app.altio.service.domain.model.ModelSource.BUILT_IN, model.source)
    assertTrue(ModelCapability.TEXT in model.capabilities)
    assertTrue(ModelCapability.AUDIO in model.capabilities)
  }

  @Test
  fun `catalog checksum matches bundled asset manifest`() {
    val assetPath =
        listOf(
                Paths.get("core/data/src/main/assets/models.json"),
                Paths.get("src/main/assets/models.json"),
            )
            .firstOrNull { Files.exists(it) }
            ?: error("Unable to locate models.json from test working directory")
    val manifest = String(Files.readAllBytes(assetPath))
    assertTrue(manifest.contains(ModelCatalog.gemma4E2bIt.version))
    assertTrue(manifest.contains(ModelCatalog.gemma4E2bIt.sha256))
    assertTrue(manifest.contains(ModelCatalog.gemma4E2bIt.files.first().sha256))
    assertTrue(manifest.contains(ModelCatalog.qwen352bInstruct.version))
    assertTrue(manifest.contains(ModelCatalog.qwen352bInstruct.sha256))
    assertTrue(manifest.contains(ModelCatalog.qwen352bInstruct.files.first().sha256))
    assertTrue(manifest.contains(ModelCatalog.qwen3508bInstruct.version))
    assertTrue(manifest.contains(ModelCatalog.qwen3508bInstruct.sha256))
    assertTrue(manifest.contains(ModelCatalog.qwen3508bInstruct.files.first().sha256))
  }

  @Test
  fun `gemma 4 e2b it uses refreshed post may 5 build metadata`() {
    val model = ModelCatalog.gemma4E2bIt
    assertEquals(ModelCatalog.GEMMA_4_E2B_IT_VERSION, model.version)
    assertEquals(ModelCatalog.GEMMA_4_E2B_IT_SHA256, model.sha256)
    assertEquals(ModelCatalog.GEMMA_4_E2B_IT_SHA256, model.files.first().sha256)
  }
}
