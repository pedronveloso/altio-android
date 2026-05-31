/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.power

import java.util.Locale

internal fun detectOemFamily(manufacturer: String?, brand: String?): OemFamily {
  val normalizedManufacturer = manufacturer.orEmpty().lowercase(Locale.ROOT)
  val normalizedBrand = brand.orEmpty().lowercase(Locale.ROOT)
  return when {
    normalizedManufacturer.contains("xiaomi") || normalizedBrand.contains("xiaomi") ->
        OemFamily.XIAOMI
    normalizedManufacturer.contains("redmi") || normalizedBrand.contains("redmi") -> OemFamily.REDMI
    normalizedManufacturer.contains("poco") || normalizedBrand.contains("poco") -> OemFamily.POCO
    else -> OemFamily.UNKNOWN
  }
}
