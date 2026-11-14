package com.nexus.sync.domain.usecase

import com.nexus.sync.domain.model.ConflictResolutionResult
import com.nexus.sync.domain.model.ConflictResolutionStrategy
import com.nexus.sync.domain.model.InventoryItem
import com.nexus.sync.domain.model.SyncStatus

class ResolveConflictUseCase {

    operator fun invoke(
        localItem: InventoryItem,
        remoteItem: InventoryItem,
        strategy: ConflictResolutionStrategy
    ): ConflictResolutionResult {
        return when (strategy) {
            ConflictResolutionStrategy.SERVER_AUTHORITATIVE -> {
                ConflictResolutionResult(
                    resolvedItem = remoteItem.copy(syncStatus = SyncStatus.SYNCED),
                    winner = ConflictResolutionResult.Winner.SERVER,
                    strategyApplied = strategy
                )
            }

            ConflictResolutionStrategy.LAST_WRITE_WINS -> {
                if (localItem.lastModifiedEpoch >= remoteItem.lastModifiedEpoch) {
                    // Local write is newer or simultaneous -> Local wins!
                    ConflictResolutionResult(
                        resolvedItem = localItem.copy(
                            version = maxOf(localItem.version, remoteItem.version) + 1,
                            syncStatus = SyncStatus.PENDING_SYNC
                        ),
                        winner = ConflictResolutionResult.Winner.LOCAL,
                        strategyApplied = strategy
                    )
                } else {
                    // Server write is newer -> Server wins!
                    ConflictResolutionResult(
                        resolvedItem = remoteItem.copy(syncStatus = SyncStatus.SYNCED),
                        winner = ConflictResolutionResult.Winner.SERVER,
                        strategyApplied = strategy
                    )
                }
            }
        }
    }
}
