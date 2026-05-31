/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientTokenDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: ClientTokenEntity)

  @Query("SELECT * FROM client_tokens WHERE tokenHash = :hash")
  suspend fun findByHash(hash: String): ClientTokenEntity?

  @Query("SELECT * FROM client_tokens") suspend fun getAll(): List<ClientTokenEntity>

  @Query("SELECT COUNT(*) FROM client_tokens") suspend fun countRows(): Int

  @Query("UPDATE client_tokens SET lastUsedAt = :nowMs WHERE tokenHash = :hash")
  suspend fun updateLastUsed(hash: String, nowMs: Long)

  @Query("UPDATE client_tokens SET isRevoked = 1 WHERE tokenHash = :hash")
  suspend fun revoke(hash: String)

  @Query("SELECT * FROM client_tokens WHERE isRevoked = 0 ORDER BY createdAt DESC")
  fun observeActive(): Flow<List<ClientTokenEntity>>

  @Query("DELETE FROM client_tokens WHERE tokenHash = :hash") suspend fun delete(hash: String)
}
