/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.db

import androidx.room.migration.Migration

/**
 * Central registry for Room migrations. Keep these lists explicit so schema changes fail fast
 * during development instead of silently clearing stored state.
 */
object DatabaseMigrations {
  val runtime: Array<Migration> = emptyArray()
  val durable: Array<Migration> = emptyArray()
}
