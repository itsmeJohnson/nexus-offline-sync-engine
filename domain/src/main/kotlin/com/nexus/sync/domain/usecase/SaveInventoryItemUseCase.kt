package com.nexus.sync.domain.usecase

import com.nexus.sync.domain.model.InventoryItem
import com.nexus.sync.domain.model.SyncOperation
import com.nexus.sync.domain.model.SyncOperationType
import com.nexus.sync.domain.model.SyncStatus
import com.nexus.sync.domain.repository.InventoryRepository
import com.nexus.sync.domain.repository.SyncOutboxRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class SaveInventoryItemUseCase(
    private val inventoryRepository: InventoryRepository,
    private val outboxRepository: SyncOutboxRepository,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend operator fun invoke(item: InventoryItem): InventoryItem {
        val existing = inventoryRepository.getItemById(item.id)
        val now = System.currentTimeMillis()

        val itemToPersist = item.copy(
            lastModifiedEpoch = now,
            syncStatus = SyncStatus.PENDING_SYNC,
            version = if (existing != null) existing.version + 1 else 1L,
            isDeleted = false
        )

        // 1. Optimistic local persistence
        inventoryRepository.upsertItem(itemToPersist)

        // 2. Enqueue in Outbox queue with idempotency key
        val opType = if (existing == null) SyncOperationType.CREATE else SyncOperationType.UPDATE
        val operation = SyncOperation(
            entityId = itemToPersist.id,
            operationType = opType,
            payloadJson = json.encodeToString(itemToPersist),
            idempotencyKey = "op-${itemToPersist.id}-${itemToPersist.version}-${UUID.randomUUID()}",
            timestampEpoch = now
        )
        outboxRepository.enqueueOperation(operation)

        return itemToPersist
    }
}
