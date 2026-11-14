package com.nexus.sync.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexus.sync.domain.model.SyncOperation
import com.nexus.sync.domain.model.SyncOperationType

@Entity(
    tableName = "pending_sync_queue",
    indices = [
        Index(value = ["createdAtEpoch"]),
        Index(value = ["entityId"]),
        Index(value = ["idempotencyKey"], unique = true)
    ]
)
data class PendingSyncEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val entityId: String,
    val entityType: String = "INVENTORY_ITEM",
    val operationType: String,
    val payloadJson: String,
    val idempotencyKey: String,
    val createdAtEpoch: Long,
    val retryCount: Int = 0,
    val lastError: String? = null
) {
    fun toDomain(): SyncOperation {
        return SyncOperation(
            id = id,
            entityId = entityId,
            operationType = try { SyncOperationType.valueOf(operationType) } catch (_: Exception) { SyncOperationType.UPDATE },
            payloadJson = payloadJson,
            idempotencyKey = idempotencyKey,
            timestampEpoch = createdAtEpoch,
            retryCount = retryCount,
            lastError = lastError
        )
    }

    companion object {
        fun fromDomain(domain: SyncOperation): PendingSyncEntity {
            return PendingSyncEntity(
                id = domain.id,
                entityId = domain.entityId,
                operationType = domain.operationType.name,
                payloadJson = domain.payloadJson,
                idempotencyKey = domain.idempotencyKey,
                createdAtEpoch = domain.timestampEpoch,
                retryCount = domain.retryCount,
                lastError = domain.lastError
            )
        }
    }
}
