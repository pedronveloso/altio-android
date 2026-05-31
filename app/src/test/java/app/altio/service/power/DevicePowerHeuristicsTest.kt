/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.power

import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DevicePowerHeuristicsTest {

  @Test
  fun `detectOemFamily matches Xiaomi manufacturer`() {
    assertEquals(OemFamily.XIAOMI, detectOemFamily(manufacturer = "Xiaomi", brand = "altio"))
  }

  @Test
  fun `detectOemFamily matches Redmi brand`() {
    assertEquals(OemFamily.REDMI, detectOemFamily(manufacturer = "foo", brand = "Redmi"))
  }

  @Test
  fun `detectOemFamily matches Poco regardless of case`() {
    assertEquals(OemFamily.POCO, detectOemFamily(manufacturer = "POCO", brand = "bar"))
  }

  @Test
  fun `detectOemFamily is locale-stable for Turkish casing`() {
    val previousLocale = Locale.getDefault()
    Locale.setDefault(Locale.forLanguageTag("tr-TR"))
    try {
      assertEquals(OemFamily.XIAOMI, detectOemFamily(manufacturer = "XIAOMI", brand = "bar"))
    } finally {
      Locale.setDefault(previousLocale)
    }
  }

  @Test
  fun `detectOemFamily falls back to unknown`() {
    assertEquals(OemFamily.UNKNOWN, detectOemFamily(manufacturer = "Google", brand = "Pixel"))
  }
}
