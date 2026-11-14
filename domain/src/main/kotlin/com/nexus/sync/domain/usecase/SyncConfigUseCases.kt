package com.nexus.sync.domain.usecase

import com.nexus.sync.domain.model.ConflictResolutionStrategy
import com.nexus.sync.domain.model.SyncConfig
import com.nexus.sync.domain.repository.SyncConfigRepository
import com.nexus.sync.domain.repository.SyncOutboxRepository
import kotlinx.coroutines.flow.Flow

class ObserveSyncConfigUseCase(
    private val syncConfigRepository: SyncConfigRepository
) {
    operator fun invoke(): Flow<SyncConfig> = syncConfigRepository.observeSyncConfig()
}

class UpdateSyncConfigUseCase(
    private val syncConfigRepository: SyncConfigRepository
) {
    suspend fun updateConflictStrategy(strategy: ConflictResolutionStrategy) {
        syncConfigRepository.updateConflictStrategy(strategy)
    }

    suspend fun updateSyncInterval(minutes: Int) {
        syncConfigRepository.updateSyncInterval(minutes)
    }

    suspend fun updateBatchSize(batchSize: Int) {
        syncConfigRepository.updateBatchSize(batchSize)
    }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        syncConfigRepository.setAutoSyncEnabled(enabled)
    }
}

class ObservePendingSyncCountUseCase(
    private val outboxRepository: SyncOutboxRepository
) {
    operator fun invoke(): Flow<Int> = outboxRepository.observePendingOperationsCount()
}
