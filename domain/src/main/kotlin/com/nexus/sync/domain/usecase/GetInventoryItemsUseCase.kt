package com.nexus.sync.domain.usecase

import com.nexus.sync.domain.model.InventoryItem
import com.nexus.sync.domain.repository.InventoryRepository
import kotlinx.coroutines.flow.Flow

class GetInventoryItemsUseCase(
    private val inventoryRepository: InventoryRepository
) {
    operator fun invoke(): Flow<List<InventoryItem>> {
        return inventoryRepository.observeAllActiveItems()
    }
}
