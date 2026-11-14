package com.nexus.sync.domain.usecase

import com.nexus.sync.domain.model.ConflictResolutionResult
import com.nexus.sync.domain.model.SyncOperationType
import com.nexus.sync.domain.model.SyncResult
import com.nexus.sync.domain.model.SyncStatus
import com.nexus.sync.domain.repository.InventoryRepository
import com.nexus.sync.domain.repository.RemoteInventoryDataSource
import com.nexus.sync.domain.repository.SyncConfigRepository
import com.nexus.sync.domain.repository.SyncOutboxRepository

class PerformTwoWaySyncUseCase(
    private val inventoryRepository: InventoryRepository,
    private val outboxRepository: SyncOutboxRepository,
    private val configRepository: SyncConfigRepository,
    private val remoteDataSource: RemoteInventoryDataSource,
    private val resolveConflictUseCase: ResolveConflictUseCase
) {
    suspend operator fun invoke(): SyncResult {
        val config = configRepository.getSyncConfig()

        var pulledCount = 0
        var pushedCount = 0
        var conflictsResolved = 0

        // -------------------------------------------------------------
        // PHASE 1: Pull Remote Deltas & Resolve Local/Remote Conflicts
        // -------------------------------------------------------------
        val deltaResult = remoteDataSource.fetchDeltaChanges(sinceEpoch = config.lastSuccessfulSyncEpoch)
        if (deltaResult.isFailure) {
            val ex = deltaResult.exceptionOrNull()
            return SyncResult.Failure(
                reason = "Failed to fetch remote delta changes: ${ex?.message}",
                throwable = ex
            )
        }

        val remoteItems = deltaResult.getOrDefault(emptyList())
        for (remoteItem in remoteItems) {
            val localItem = inventoryRepository.getItemById(remoteItem.id)

            if (localItem != null && localItem.syncStatus == SyncStatus.PENDING_SYNC) {
                // Conflict detected!
                val conflictResult = resolveConflictUseCase(
                    localItem = localItem,
                    remoteItem = remoteItem,
                    strategy = config.conflictStrategy
                )
                conflictsResolved++

                if (conflictResult.winner == ConflictResolutionResult.Winner.SERVER) {
                    // Server wins: overwrite local DB and evict local outbox operations
                    if (remoteItem.isDeleted) {
                        inventoryRepository.hardDeleteItem(remoteItem.id)
                    } else {
                        inventoryRepository.upsertItem(conflictResult.resolvedItem)
                    }
                    outboxRepository.removeOperationsForEntity(remoteItem.id)
                } else {
                    // Local wins: preserve local item with updated version, keep outbox queued
                    inventoryRepository.upsertItem(conflictResult.resolvedItem)
                }
            } else {
                // No local conflict: directly mirror server state
                if (remoteItem.isDeleted) {
                    inventoryRepository.hardDeleteItem(remoteItem.id)
                } else {
                    inventoryRepository.upsertItem(remoteItem.copy(syncStatus = SyncStatus.SYNCED))
                }
            }
            pulledCount++
        }

        // -------------------------------------------------------------
        // PHASE 2: Push Local Outbox Operations to Remote
        // -------------------------------------------------------------
        val pendingOperations = outboxRepository.getPendingOperations(limit = config.batchSize)

        for (operation in pendingOperations) {
            val pushResult = remoteDataSource.pushOperation(operation)
            if (pushResult.isSuccess) {
                val serverAcknowledgedItem = pushResult.getOrNull()

                // Mark local item as synced if not deleted
                if (operation.operationType == SyncOperationType.DELETE) {
                    inventoryRepository.hardDeleteItem(operation.entityId)
                } else if (serverAcknowledgedItem != null) {
                    inventoryRepository.upsertItem(serverAcknowledgedItem.copy(syncStatus = SyncStatus.SYNCED))
                } else {
                    inventoryRepository.updateSyncStatus(operation.entityId, SyncStatus.SYNCED)
                }

                // Evict successfully synced operation from outbox
                outboxRepository.removeOperation(operation.id)
                pushedCount++
            } else {
                val errorMsg = pushResult.exceptionOrNull()?.message ?: "Unknown push error"
                outboxRepository.incrementRetryCount(operation.id, errorMsg)
            }
        }

        val completedTime = System.currentTimeMillis()
        configRepository.updateLastSuccessfulSync(completedTime)

        return SyncResult.Success(
            pulledCount = pulledCount,
            pushedCount = pushedCount,
            conflictsResolved = conflictsResolved,
            completedAtEpoch = completedTime
        )
    }
}
