package com.nexus.sync.domain.repository

import com.nexus.sync.domain.model.ConflictResolutionStrategy
import com.nexus.sync.domain.model.InventoryItem
import com.nexus.sync.domain.model.SyncConfig
import com.nexus.sync.domain.model.SyncOperation
import com.nexus.sync.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

interface InventoryRepository {
    fun observeAllActiveItems(): Flow<List<InventoryItem>>
    fun observeItemById(id: String): Flow<InventoryItem?>
    suspend fun getItemById(id: String): InventoryItem?
    suspend fun upsertItem(item: InventoryItem)
    suspend fun upsertItems(items: List<InventoryItem>)
    suspend fun markDeleted(id: String)
    suspend fun hardDeleteItem(id: String)
    suspend fun updateSyncStatus(id: String, status: SyncStatus)
}

interface SyncOutboxRepository {
    fun observePendingOperationsCount(): Flow<Int>
    suspend fun getPendingOperations(limit: Int): List<SyncOperation>
    suspend fun enqueueOperation(operation: SyncOperation)
    suspend fun removeOperation(id: Long)
    suspend fun removeOperationsForEntity(entityId: String)
    suspend fun incrementRetryCount(id: Long, error: String)
    suspend fun clearOutbox()
}

interface SyncConfigRepository {
    fun observeSyncConfig(): Flow<SyncConfig>
    suspend fun getSyncConfig(): SyncConfig
    suspend fun updateSyncInterval(minutes: Int)
    suspend fun updateBatchSize(batchSize: Int)
    suspend fun updateConflictStrategy(strategy: ConflictResolutionStrategy)
    suspend fun updateAuthSession(token: String, expiryEpoch: Long)
    suspend fun updateLastSuccessfulSync(epoch: Long)
    suspend fun setAutoSyncEnabled(enabled: Boolean)
}

interface RemoteInventoryDataSource {
    suspend fun fetchDeltaChanges(sinceEpoch: Long): Result<List<InventoryItem>>
    suspend fun pushOperation(operation: SyncOperation): Result<InventoryItem>
}
