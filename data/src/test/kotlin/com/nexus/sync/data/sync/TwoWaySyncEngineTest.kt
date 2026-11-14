package com.nexus.sync.data.sync

import com.nexus.sync.domain.model.ConflictResolutionStrategy
import com.nexus.sync.domain.model.InventoryItem
import com.nexus.sync.domain.model.SyncConfig
import com.nexus.sync.domain.model.SyncOperation
import com.nexus.sync.domain.model.SyncOperationType
import com.nexus.sync.domain.model.SyncResult
import com.nexus.sync.domain.model.SyncStatus
import com.nexus.sync.domain.repository.InventoryRepository
import com.nexus.sync.domain.repository.RemoteInventoryDataSource
import com.nexus.sync.domain.repository.SyncConfigRepository
import com.nexus.sync.domain.repository.SyncOutboxRepository
import com.nexus.sync.domain.usecase.PerformTwoWaySyncUseCase
import com.nexus.sync.domain.usecase.ResolveConflictUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TwoWaySyncEngineTest {

    private val inventoryRepository = mockk<InventoryRepository>(relaxed = true)
    private val outboxRepository = mockk<SyncOutboxRepository>(relaxed = true)
    private val configRepository = mockk<SyncConfigRepository>(relaxed = true)
    private val remoteDataSource = mockk<RemoteInventoryDataSource>(relaxed = true)
    private val resolveConflictUseCase = ResolveConflictUseCase()

    private lateinit var performTwoWaySyncUseCase: PerformTwoWaySyncUseCase

    @BeforeEach
    fun setUp() {
        performTwoWaySyncUseCase = PerformTwoWaySyncUseCase(
            inventoryRepository = inventoryRepository,
            outboxRepository = outboxRepository,
            configRepository = configRepository,
            remoteDataSource = remoteDataSource,
            resolveConflictUseCase = resolveConflictUseCase
        )

        coEvery { configRepository.getSyncConfig() } returns SyncConfig(
            batchSize = 50,
            conflictStrategy = ConflictResolutionStrategy.LAST_WRITE_WINS,
            lastSuccessfulSyncEpoch = 1000L
        )
    }

    @Test
    fun `performTwoWaySync pulls remote delta and pushes outbox queue successfully`() = runTest {
        val now = System.currentTimeMillis()

        // 1. Remote delta changes
        val remoteItem = InventoryItem(
            id = "item-server-1",
            sku = "SKU-S1",
            name = "Server Created Item",
            category = "Networking",
            quantity = 50,
            unitPrice = 199.0,
            location = "Central Depot",
            lastModifiedEpoch = now,
            syncStatus = SyncStatus.SYNCED
        )
        coEvery { remoteDataSource.fetchDeltaChanges(1000L) } returns Result.success(listOf(remoteItem))
        coEvery { inventoryRepository.getItemById("item-server-1") } returns null

        // 2. Pending local outbox operation
        val pendingOp = SyncOperation(
            id = 42L,
            entityId = "item-local-1",
            operationType = SyncOperationType.CREATE,
            payloadJson = "{}",
            idempotencyKey = "idemp-key-42",
            timestampEpoch = now
        )
        coEvery { outboxRepository.getPendingOperations(50) } returns listOf(pendingOp)

        val acknowledgedItem = InventoryItem(
            id = "item-local-1",
            sku = "SKU-L1",
            name = "Local Item",
            category = "Power",
            quantity = 10,
            unitPrice = 50.0,
            location = "Shelf 1",
            lastModifiedEpoch = now,
            syncStatus = SyncStatus.SYNCED
        )
        coEvery { remoteDataSource.pushOperation(pendingOp) } returns Result.success(acknowledgedItem)

        // Execute sync
        val result = performTwoWaySyncUseCase()

        assertInstanceOf(SyncResult.Success::class.java, result)
        val success = result as SyncResult.Success
        assertEquals(1, success.pulledCount)
        assertEquals(1, success.pushedCount)
        assertEquals(0, success.conflictsResolved)

        // Verifications
        coVerify(exactly = 1) { inventoryRepository.upsertItem(match { it.id == "item-server-1" && it.syncStatus == SyncStatus.SYNCED }) }
        coVerify(exactly = 1) { remoteDataSource.pushOperation(pendingOp) }
        coVerify(exactly = 1) { outboxRepository.removeOperation(42L) }
        coVerify(exactly = 1) { configRepository.updateLastSuccessfulSync(any()) }
    }

    @Test
    fun `performTwoWaySync resolves conflicts using Last-Write-Wins and evicts outbox when server wins`() = runTest {
        val now = System.currentTimeMillis()

        // Local item has pending sync but older timestamp
        val localItem = InventoryItem(
            id = "item-conflict-1",
            sku = "SKU-C1",
            name = "Local Name (Older)",
            category = "Cables",
            quantity = 10,
            unitPrice = 20.0,
            location = "Bin 1",
            lastModifiedEpoch = now - 5000L,
            syncStatus = SyncStatus.PENDING_SYNC,
            version = 1L
        )
        coEvery { inventoryRepository.getItemById("item-conflict-1") } returns localItem

        // Remote item has newer timestamp
        val remoteItem = InventoryItem(
            id = "item-conflict-1",
            sku = "SKU-C1",
            name = "Remote Name (Newer Winner)",
            category = "Cables",
            quantity = 15,
            unitPrice = 25.0,
            location = "Bin 1",
            lastModifiedEpoch = now, // Newer!
            syncStatus = SyncStatus.SYNCED,
            version = 2L
        )
        coEvery { remoteDataSource.fetchDeltaChanges(any()) } returns Result.success(listOf(remoteItem))
        coEvery { outboxRepository.getPendingOperations(any()) } returns emptyList()

        val result = performTwoWaySyncUseCase()

        assertInstanceOf(SyncResult.Success::class.java, result)
        val success = result as SyncResult.Success
        assertEquals(1, success.conflictsResolved)

        // Server won: local outbox for this entity was evicted!
        coVerify(exactly = 1) { outboxRepository.removeOperationsForEntity("item-conflict-1") }
        coVerify(exactly = 1) {
            inventoryRepository.upsertItem(match {
                it.id == "item-conflict-1" && it.name == "Remote Name (Newer Winner)" && it.syncStatus == SyncStatus.SYNCED
            })
        }
    }

    @Test
    fun `performTwoWaySync increments retryCount when push operation fails`() = runTest {
        val now = System.currentTimeMillis()
        coEvery { remoteDataSource.fetchDeltaChanges(any()) } returns Result.success(emptyList())

        val failingOp = SyncOperation(
            id = 88L,
            entityId = "item-fail",
            operationType = SyncOperationType.UPDATE,
            payloadJson = "{}",
            idempotencyKey = "key-88",
            timestampEpoch = now
        )
        coEvery { outboxRepository.getPendingOperations(any()) } returns listOf(failingOp)
        coEvery { remoteDataSource.pushOperation(failingOp) } returns Result.failure(Exception("HTTP 503 Service Unavailable"))

        val result = performTwoWaySyncUseCase()

        assertInstanceOf(SyncResult.Success::class.java, result)
        val success = result as SyncResult.Success
        assertEquals(0, success.pushedCount)

        coVerify(exactly = 1) { outboxRepository.incrementRetryCount(88L, match { it.contains("503") }) }
        coVerify(exactly = 0) { outboxRepository.removeOperation(88L) }
    }
}
