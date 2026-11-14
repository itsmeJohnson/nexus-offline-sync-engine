package com.nexus.sync.data.repository

import com.nexus.sync.data.database.dao.PendingSyncDao
import com.nexus.sync.data.database.entity.PendingSyncEntity
import com.nexus.sync.domain.model.SyncOperation
import com.nexus.sync.domain.repository.SyncOutboxRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncOutboxRepositoryImpl @Inject constructor(
    private val pendingSyncDao: PendingSyncDao
) : SyncOutboxRepository {

    override fun observePendingOperationsCount(): Flow<Int> {
        return pendingSyncDao.observeCount()
    }

    override suspend fun getPendingOperations(limit: Int): List<SyncOperation> {
        return pendingSyncDao.getPendingOperations(limit).map { it.toDomain() }
    }

    override suspend fun enqueueOperation(operation: SyncOperation) {
        pendingSyncDao.insert(PendingSyncEntity.fromDomain(operation))
    }

    override suspend fun removeOperation(id: Long) {
        pendingSyncDao.deleteById(id)
    }

    override suspend fun removeOperationsForEntity(entityId: String) {
        pendingSyncDao.deleteByEntityId(entityId)
    }

    override suspend fun incrementRetryCount(id: Long, error: String) {
        pendingSyncDao.incrementRetry(id, error)
    }

    override suspend fun clearOutbox() {
        pendingSyncDao.clear()
    }
}
