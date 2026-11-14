package com.nexus.sync.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class InventoryItem(
    val id: String,
    val sku: String,
    val name: String,
    val category: String,
    val quantity: Int,
    val unitPrice: Double,
    val location: String,
    val lastModifiedEpoch: Long,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val version: Long = 1L,
    val isDeleted: Boolean = false
) {
    val isLowStock: Boolean
        get() = quantity <= 5
}

@Serializable
data class SyncOperation(
    val id: Long = 0L,
    val entityId: String,
    val operationType: SyncOperationType,
    val payloadJson: String,
    val idempotencyKey: String,
    val timestampEpoch: Long,
    val retryCount: Int = 0,
    val lastError: String? = null
)

data class SyncConfig(
    val syncIntervalMinutes: Int = 15,
    val batchSize: Int = 50,
    val conflictStrategy: ConflictResolutionStrategy = ConflictResolutionStrategy.LAST_WRITE_WINS,
    val authToken: String = "",
    val tokenExpiryEpoch: Long = 0L,
    val lastSuccessfulSyncEpoch: Long = 0L,
    val autoSyncEnabled: Boolean = true
)

sealed interface SyncResult {
    data class Success(
        val pulledCount: Int,
        val pushedCount: Int,
        val conflictsResolved: Int,
        val completedAtEpoch: Long
    ) : SyncResult

    data class Failure(
        val reason: String,
        val throwable: Throwable? = null
    ) : SyncResult
}

data class ConflictResolutionResult(
    val resolvedItem: InventoryItem,
    val winner: Winner,
    val strategyApplied: ConflictResolutionStrategy
) {
    enum class Winner {
        LOCAL,
        SERVER
    }
}
