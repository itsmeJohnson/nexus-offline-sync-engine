package com.nexus.sync.feature.inventory.presentation

import app.cash.turbine.test
import com.nexus.sync.data.sync.WorkManagerSyncScheduler
import com.nexus.sync.domain.model.ConflictResolutionStrategy
import com.nexus.sync.domain.model.InventoryItem
import com.nexus.sync.domain.model.SyncConfig
import com.nexus.sync.domain.model.SyncResult
import com.nexus.sync.domain.model.SyncStatus
import com.nexus.sync.domain.usecase.DeleteInventoryItemUseCase
import com.nexus.sync.domain.usecase.GetInventoryItemsUseCase
import com.nexus.sync.domain.usecase.ObservePendingSyncCountUseCase
import com.nexus.sync.domain.usecase.ObserveSyncConfigUseCase
import com.nexus.sync.domain.usecase.PerformTwoWaySyncUseCase
import com.nexus.sync.domain.usecase.SaveInventoryItemUseCase
import com.nexus.sync.domain.usecase.UpdateSyncConfigUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val getInventoryItemsUseCase = mockk<GetInventoryItemsUseCase>()
    private val saveInventoryItemUseCase = mockk<SaveInventoryItemUseCase>(relaxed = true)
    private val deleteInventoryItemUseCase = mockk<DeleteInventoryItemUseCase>(relaxed = true)
    private val performTwoWaySyncUseCase = mockk<PerformTwoWaySyncUseCase>()
    private val observeSyncConfigUseCase = mockk<ObserveSyncConfigUseCase>()
    private val updateSyncConfigUseCase = mockk<UpdateSyncConfigUseCase>(relaxed = true)
    private val observePendingSyncCountUseCase = mockk<ObservePendingSyncCountUseCase>()
    private val syncScheduler = mockk<WorkManagerSyncScheduler>(relaxed = true)

    private val itemsFlow = MutableStateFlow<List<InventoryItem>>(emptyList())
    private val pendingCountFlow = MutableStateFlow(0)
    private val configFlow = MutableStateFlow(SyncConfig())

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        coEvery { getInventoryItemsUseCase() } returns itemsFlow
        coEvery { observePendingSyncCountUseCase() } returns pendingCountFlow
        coEvery { observeSyncConfigUseCase() } returns configFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): InventoryViewModel {
        return InventoryViewModel(
            getInventoryItemsUseCase = getInventoryItemsUseCase,
            saveInventoryItemUseCase = saveInventoryItemUseCase,
            deleteInventoryItemUseCase = deleteInventoryItemUseCase,
            performTwoWaySyncUseCase = performTwoWaySyncUseCase,
            observeSyncConfigUseCase = observeSyncConfigUseCase,
            updateSyncConfigUseCase = updateSyncConfigUseCase,
            observePendingSyncCountUseCase = observePendingSyncCountUseCase,
            syncScheduler = syncScheduler
        )
    }

    @Test
    fun `initial uiState combines items and pending outbox count with Turbine`() = runTest {
        val sampleItems = listOf(
            InventoryItem(
                id = "item-1",
                sku = "SKU-1",
                name = "Core Switch",
                category = "Networking",
                quantity = 15,
                unitPrice = 500.0,
                location = "Rack A",
                lastModifiedEpoch = 1000L,
                syncStatus = SyncStatus.SYNCED
            ),
            InventoryItem(
                id = "item-2",
                sku = "SKU-2",
                name = "Patch Cable",
                category = "Cables",
                quantity = 2, // Low stock!
                unitPrice = 10.0,
                location = "Bin 4",
                lastModifiedEpoch = 1000L,
                syncStatus = SyncStatus.PENDING_SYNC
            )
        )
        itemsFlow.value = sampleItems
        pendingCountFlow.value = 1

        val viewModel = createViewModel()

        viewModel.uiState.test {
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem()

            assertEquals(2, state.items.size)
            assertEquals(2, state.totalCount)
            assertEquals(1, state.pendingSyncCount)
            assertFalse(state.isSyncing)
            assertEquals(InventoryFilter.ALL, state.activeFilter)
        }
    }

    @Test
    fun `FilterSelected to LOW_STOCK filters items correctly`() = runTest {
        val sampleItems = listOf(
            InventoryItem(id = "1", sku = "S1", name = "A", category = "C", quantity = 20, unitPrice = 1.0, location = "L", lastModifiedEpoch = 0),
            InventoryItem(id = "2", sku = "S2", name = "B", category = "C", quantity = 3, unitPrice = 1.0, location = "L", lastModifiedEpoch = 0)
        )
        itemsFlow.value = sampleItems

        val viewModel = createViewModel()

        viewModel.uiState.test {
            testDispatcher.scheduler.advanceUntilIdle()
            val initialState = awaitItem()
            assertEquals(2, initialState.items.size)

            viewModel.onIntent(InventoryIntent.FilterSelected(InventoryFilter.LOW_STOCK))
            testDispatcher.scheduler.advanceUntilIdle()

            val filteredState = awaitItem()
            assertEquals(1, filteredState.items.size)
            assertEquals("S2", filteredState.items.first().sku)
        }
    }

    @Test
    fun `TriggerSync toggles isSyncing and emits ShowSnackbar effect upon success`() = runTest {
        coEvery { performTwoWaySyncUseCase() } returns SyncResult.Success(
            pulledCount = 3,
            pushedCount = 2,
            conflictsResolved = 1,
            completedAtEpoch = 5000L
        )

        val viewModel = createViewModel()

        viewModel.uiEffect.test {
            viewModel.onIntent(InventoryIntent.TriggerSync)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is InventoryUiEffect.ShowSnackbar)
            val snackbar = effect as InventoryUiEffect.ShowSnackbar
            assertTrue(snackbar.message.contains("Sync completed"))
            assertTrue(snackbar.message.contains("2 pushed"))
            assertTrue(snackbar.message.contains("3 pulled"))
        }

        coVerify(exactly = 1) { performTwoWaySyncUseCase() }
    }
}
