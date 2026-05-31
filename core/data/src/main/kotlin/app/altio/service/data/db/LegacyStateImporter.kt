/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.db

import android.content.Context
import androidx.room.Room
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class LegacyStateImporter(
    private val appContext: Context,
    private val durableDatabase: DurableStateDatabase,
) {
  suspend fun importIfNeeded() =
      withContext(Dispatchers.IO) {
        if (!legacyDatabaseFile().exists()) return@withContext
        val durableModelCount = durableDatabase.modelDao().countRows()
        val durableTokenCount = durableDatabase.clientTokenDao().countRows()
        if (durableModelCount > 0 && durableTokenCount > 0) {
          return@withContext
        }

        val legacyDatabase =
            Room.databaseBuilder(appContext, LegacyCombinedDatabase::class.java, LEGACY_DB_NAME)
                .build()

        try {
          val importedModels =
              if (durableModelCount == 0) {
                legacyDatabase.modelDao().getAll().also { legacyModels ->
                  for (model in legacyModels) {
                    durableDatabase.modelDao().upsert(model)
                  }
                }
              } else {
                emptyList()
              }
          val importedTokens =
              if (durableTokenCount == 0) {
                legacyDatabase.clientTokenDao().getAll().also { legacyTokens ->
                  for (token in legacyTokens) {
                    durableDatabase.clientTokenDao().insert(token)
                  }
                }
              } else {
                emptyList()
              }

          Timber.i(
              "Imported %d model rows and %d token rows from legacy state database",
              importedModels.size,
              importedTokens.size,
          )
        } finally {
          legacyDatabase.close()
        }
      }

  private fun legacyDatabaseFile(): File = appContext.getDatabasePath(LEGACY_DB_NAME)

  companion object {
    private const val LEGACY_DB_NAME = "ai_service.db"
  }
}
