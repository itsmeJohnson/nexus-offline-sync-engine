package com.nexus.sync.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nexus.sync.data.database.entity.PendingSyncEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingSyncDao {

    @Query("SELECT COUNT(*) FROM pending_sync_queue")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_sync_queue")
    suspend fun getCount(): Int

    @Query("SELECT * FROM pending_sync_queue ORDER BY createdAtEpoch ASC LIMIT :limit")
    suspend fun getPendingOperations(limit: Int): List<PendingSyncEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingSyncEntity): Long

    @Query("DELETE FROM pending_sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_sync_queue WHERE entityId = :entityId")
    suspend fun deleteByEntityId(entityId: String)

    @Query("UPDATE pending_sync_queue SET retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun incrementRetry(id: Long, error: String)

    @Query("DELETE FROM pending_sync_queue")
    suspend fun clear()
}
