package com.nexus.sync.data.repository

import com.nexus.sync.data.database.dao.InventoryDao
import com.nexus.sync.data.database.entity.InventoryEntity
import com.nexus.sync.domain.model.InventoryItem
import com.nexus.sync.domain.model.SyncStatus
import com.nexus.sync.domain.repository.InventoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepositoryImpl @Inject constructor(
    private val inventoryDao: InventoryDao
) : InventoryRepository {

    override fun observeAllActiveItems(): Flow<List<InventoryItem>> {
        return inventoryDao.observeAllActiveItems().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeItemById(id: String): Flow<InventoryItem?> {
        return inventoryDao.observeItemById(id).map { it?.toDomain() }
    }

    override suspend fun getItemById(id: String): InventoryItem? {
        return inventoryDao.getItemById(id)?.toDomain()
    }

    override suspend fun upsertItem(item: InventoryItem) {
        inventoryDao.upsert(InventoryEntity.fromDomain(item))
    }

    override suspend fun upsertItems(items: List<InventoryItem>) {
        inventoryDao.upsertAll(items.map { InventoryEntity.fromDomain(it) })
    }

    override suspend fun markDeleted(id: String) {
        inventoryDao.markDeleted(id = id, syncStatus = SyncStatus.PENDING_SYNC.name)
    }

    override suspend fun hardDeleteItem(id: String) {
        inventoryDao.hardDelete(id)
    }

    override suspend fun updateSyncStatus(id: String, status: SyncStatus) {
        inventoryDao.updateSyncStatus(id, status.name)
    }
}
