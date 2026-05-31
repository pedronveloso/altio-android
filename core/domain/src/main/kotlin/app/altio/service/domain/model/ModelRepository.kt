/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.model

import kotlinx.coroutines.flow.Flow

interface ModelRepository {
  fun getAvailableModels(): Flow<List<Model>>

  fun getModel(id: String): Flow<Model?>

  fun getDownloadProgress(id: String): Flow<DownloadProgress>

  suspend fun startDownload(id: String)

  suspend fun pauseDownload(id: String)

  suspend fun resumeDownload(id: String)

  suspend fun cancelDownload(id: String)

  suspend fun deleteModel(id: String)

  suspend fun verifyModel(id: String): Boolean

  suspend fun setStatus(id: String, status: ModelStatus)

  suspend fun reconcileModelsOnStartup() {}
}
