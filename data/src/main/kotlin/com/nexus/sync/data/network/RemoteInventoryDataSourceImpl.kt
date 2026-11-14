package com.nexus.sync.data.network

import com.nexus.sync.domain.model.InventoryItem
import com.nexus.sync.domain.model.SyncOperation
import com.nexus.sync.domain.model.SyncOperationType
import com.nexus.sync.domain.model.SyncStatus
import com.nexus.sync.domain.repository.RemoteInventoryDataSource
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteInventoryDataSourceImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : RemoteInventoryDataSource {

    // In-memory simulated remote server state with concurrent thread safety
    private val serverStore = ConcurrentHashMap<String, InventoryItem>()
    private val processedIdempotencyKeys = ConcurrentHashMap.newKeySet<String>()

    init {
        // Pre-populate enterprise mock server catalog
        val now = System.currentTimeMillis() - 3600_000L
        val initialItems = listOf(
            InventoryItem(
                id = "inv-001",
                sku = "SKU-NEXUS-X1",
                name = "Enterprise Edge Gateway Server",
                category = "Networking",
                quantity = 14,
                unitPrice = 1299.99,
                location = "Warehouse Alpha - Bay 4",
                lastModifiedEpoch = now,
                syncStatus = SyncStatus.SYNCED,
                version = 1L
            ),
            InventoryItem(
                id = "inv-002",
                sku = "SKU-OPTIC-LC10",
                name = "Fiber Optic Transceiver 100G",
                category = "Cables & Optics",
                quantity = 3, // Low stock!
                unitPrice = 349.50,
                location = "Warehouse Alpha - Shelf B2",
                lastModifiedEpoch = now,
                syncStatus = SyncStatus.SYNCED,
                version = 1L
            ),
            InventoryItem(
                id = "inv-003",
                sku = "SKU-PWR-RACK3",
                name = "Smart Switched PDU 30A 208V",
                category = "Power Systems",
                quantity = 28,
                unitPrice = 850.00,
                location = "Warehouse Beta - Section 1",
                lastModifiedEpoch = now,
                syncStatus = SyncStatus.SYNCED,
                version = 1L
            )
        )
        initialItems.forEach { serverStore[it.id] = it }
    }

    override suspend fun fetchDeltaChanges(sinceEpoch: Long): Result<List<InventoryItem>> {
        return try {
            // Simulate enterprise network latency
            delay(150)
            val deltas = serverStore.values.filter { it.lastModifiedEpoch > sinceEpoch }
            Result.success(deltas)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun pushOperation(operation: SyncOperation): Result<InventoryItem> {
        return try {
            delay(100)

            // Idempotency check: If key already processed, return existing state safely
            if (processedIdempotencyKeys.contains(operation.idempotencyKey)) {
                val existing = serverStore[operation.entityId]
                return if (existing != null) Result.success(existing)
                else Result.failure(IllegalStateException("Operation already processed but item not found"))
            }

            processedIdempotencyKeys.add(operation.idempotencyKey)

            when (operation.operationType) {
                SyncOperationType.CREATE, SyncOperationType.UPDATE -> {
                    val parsedItem = json.decodeFromString<InventoryItem>(operation.payloadJson)
                    val serverItem = parsedItem.copy(
                        lastModifiedEpoch = System.currentTimeMillis(),
                        syncStatus = SyncStatus.SYNCED,
                        version = parsedItem.version + 1
                    )
                    serverStore[serverItem.id] = serverItem
                    Result.success(serverItem)
                }

                SyncOperationType.DELETE -> {
                    val removed = serverStore.remove(operation.entityId)
                    val deletedItem = (removed ?: InventoryItem(
                        id = operation.entityId,
                        sku = "",
                        name = "",
                        category = "",
                        quantity = 0,
                        unitPrice = 0.0,
                        location = "",
                        lastModifiedEpoch = System.currentTimeMillis()
                    )).copy(isDeleted = true, syncStatus = SyncStatus.SYNCED)
                    Result.success(deletedItem)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Helper for testing simulated server updates / conflicts
    fun simulateServerUpdate(item: InventoryItem) {
        serverStore[item.id] = item
    }
}
