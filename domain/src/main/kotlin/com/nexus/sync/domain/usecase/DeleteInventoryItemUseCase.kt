package com.nexus.sync.domain.usecase

import com.nexus.sync.domain.model.SyncOperation
import com.nexus.sync.domain.model.SyncOperationType
import com.nexus.sync.domain.repository.InventoryRepository
import com.nexus.sync.domain.repository.SyncOutboxRepository
import java.util.UUID

class DeleteInventoryItemUseCase(
    private val inventoryRepository: InventoryRepository,
    private val outboxRepository: SyncOutboxRepository
) {
    suspend operator fun invoke(itemId: String) {
        val now = System.currentTimeMillis()

        // 1. Optimistic soft-delete locally
        inventoryRepository.markDeleted(itemId)

        // 2. Enqueue DELETE in Outbox queue
        val operation = SyncOperation(
            entityId = itemId,
            operationType = SyncOperationType.DELETE,
            payloadJson = "{\"id\":\"$itemId\"}",
            idempotencyKey = "del-$itemId-$now-${UUID.randomUUID()}",
            timestampEpoch = now
        )
        outboxRepository.enqueueOperation(operation)
    }
}
