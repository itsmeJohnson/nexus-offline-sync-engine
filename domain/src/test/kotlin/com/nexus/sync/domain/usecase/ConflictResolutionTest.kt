package com.nexus.sync.domain.usecase

import com.nexus.sync.domain.model.ConflictResolutionResult
import com.nexus.sync.domain.model.ConflictResolutionStrategy
import com.nexus.sync.domain.model.InventoryItem
import com.nexus.sync.domain.model.SyncStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConflictResolutionTest {

    private lateinit var resolveConflictUseCase: ResolveConflictUseCase

    @BeforeEach
    fun setUp() {
        resolveConflictUseCase = ResolveConflictUseCase()
    }

    @Test
    fun `Last-Write-Wins selects local item when local timestamp is newer`() {
        val now = System.currentTimeMillis()
        val localItem = InventoryItem(
            id = "item-101",
            sku = "SKU-SWITCH-1",
            name = "Managed 24-Port Switch (Local Edit)",
            category = "Networking",
            quantity = 12,
            unitPrice = 450.0,
            location = "Bay 1",
            lastModifiedEpoch = now + 5000L, // Newer local write!
            syncStatus = SyncStatus.PENDING_SYNC,
            version = 2L
        )

        val remoteItem = InventoryItem(
            id = "item-101",
            sku = "SKU-SWITCH-1",
            name = "Managed 24-Port Switch (Server Edit)",
            category = "Networking",
            quantity = 10,
            unitPrice = 400.0,
            location = "Bay 1",
            lastModifiedEpoch = now, // Older server write
            syncStatus = SyncStatus.SYNCED,
            version = 1L
        )

        val result = resolveConflictUseCase(
            localItem = localItem,
            remoteItem = remoteItem,
            strategy = ConflictResolutionStrategy.LAST_WRITE_WINS
        )

        assertEquals(ConflictResolutionResult.Winner.LOCAL, result.winner)
        assertEquals("Managed 24-Port Switch (Local Edit)", result.resolvedItem.name)
        assertEquals(SyncStatus.PENDING_SYNC, result.resolvedItem.syncStatus)
        assertEquals(3L, result.resolvedItem.version)
    }

    @Test
    fun `Last-Write-Wins selects server item when server timestamp is newer`() {
        val now = System.currentTimeMillis()
        val localItem = InventoryItem(
            id = "item-101",
            sku = "SKU-SWITCH-1",
            name = "Local Name",
            category = "Networking",
            quantity = 5,
            unitPrice = 400.0,
            location = "Bay 1",
            lastModifiedEpoch = now, // Older local write
            syncStatus = SyncStatus.PENDING_SYNC,
            version = 1L
        )

        val remoteItem = InventoryItem(
            id = "item-101",
            sku = "SKU-SWITCH-1",
            name = "Server Name (Authoritative Update)",
            category = "Networking",
            quantity = 25,
            unitPrice = 420.0,
            location = "Bay 2",
            lastModifiedEpoch = now + 10000L, // Newer server write!
            syncStatus = SyncStatus.SYNCED,
            version = 3L
        )

        val result = resolveConflictUseCase(
            localItem = localItem,
            remoteItem = remoteItem,
            strategy = ConflictResolutionStrategy.LAST_WRITE_WINS
        )

        assertEquals(ConflictResolutionResult.Winner.SERVER, result.winner)
        assertEquals("Server Name (Authoritative Update)", result.resolvedItem.name)
        assertEquals(SyncStatus.SYNCED, result.resolvedItem.syncStatus)
        assertEquals(3L, result.resolvedItem.version)
    }

    @Test
    fun `Server-Authoritative unconditionally selects server item regardless of timestamp`() {
        val now = System.currentTimeMillis()
        val localItem = InventoryItem(
            id = "item-202",
            sku = "SKU-ROUTER-9",
            name = "Very Recent Local Override",
            category = "Networking",
            quantity = 99,
            unitPrice = 999.0,
            location = "Local Desk",
            lastModifiedEpoch = now + 999999L, // Much newer locally
            syncStatus = SyncStatus.PENDING_SYNC,
            version = 5L
        )

        val remoteItem = InventoryItem(
            id = "item-202",
            sku = "SKU-ROUTER-9",
            name = "Server Official State",
            category = "Networking",
            quantity = 10,
            unitPrice = 850.0,
            location = "Central Warehouse",
            lastModifiedEpoch = now, // Older on server
            syncStatus = SyncStatus.SYNCED,
            version = 2L
        )

        val result = resolveConflictUseCase(
            localItem = localItem,
            remoteItem = remoteItem,
            strategy = ConflictResolutionStrategy.SERVER_AUTHORITATIVE
        )

        assertEquals(ConflictResolutionResult.Winner.SERVER, result.winner)
        assertEquals("Server Official State", result.resolvedItem.name)
        assertEquals(SyncStatus.SYNCED, result.resolvedItem.syncStatus)
    }
}
