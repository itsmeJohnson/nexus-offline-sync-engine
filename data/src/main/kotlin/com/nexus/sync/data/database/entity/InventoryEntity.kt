package com.nexus.sync.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexus.sync.domain.model.InventoryItem
import com.nexus.sync.domain.model.SyncStatus

@Entity(
    tableName = "inventory_items",
    indices = [
        Index(value = ["sku"], unique = true),
        Index(value = ["category"]),
        Index(value = ["syncStatus"]),
        Index(value = ["isDeleted"])
    ]
)
data class InventoryEntity(
    @PrimaryKey
    val id: String,
    val sku: String,
    val name: String,
    val category: String,
    val quantity: Int,
    val unitPrice: Double,
    val location: String,
    val lastModifiedEpoch: Long,
    val syncStatus: String = SyncStatus.SYNCED.name,
    val version: Long = 1L,
    val isDeleted: Boolean = false
) {
    fun toDomain(): InventoryItem {
        return InventoryItem(
            id = id,
            sku = sku,
            name = name,
            category = category,
            quantity = quantity,
            unitPrice = unitPrice,
            location = location,
            lastModifiedEpoch = lastModifiedEpoch,
            syncStatus = try { SyncStatus.valueOf(syncStatus) } catch (_: Exception) { SyncStatus.SYNCED },
            version = version,
            isDeleted = isDeleted
        )
    }

    companion object {
        fun fromDomain(domain: InventoryItem): InventoryEntity {
            return InventoryEntity(
                id = domain.id,
                sku = domain.sku,
                name = domain.name,
                category = domain.category,
                quantity = domain.quantity,
                unitPrice = domain.unitPrice,
                location = domain.location,
                lastModifiedEpoch = domain.lastModifiedEpoch,
                syncStatus = domain.syncStatus.name,
                version = domain.version,
                isDeleted = domain.isDeleted
            )
        }
    }
}
