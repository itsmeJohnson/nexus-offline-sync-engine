package com.nexus.sync.feature.inventory.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.sync.data.sync.WorkManagerSyncScheduler
import com.nexus.sync.domain.model.ConflictResolutionStrategy
import com.nexus.sync.domain.model.InventoryItem
import com.nexus.sync.domain.model.SyncResult
import com.nexus.sync.domain.model.SyncStatus
import com.nexus.sync.domain.usecase.DeleteInventoryItemUseCase
import com.nexus.sync.domain.usecase.GetInventoryItemsUseCase
import com.nexus.sync.domain.usecase.ObservePendingSyncCountUseCase
import com.nexus.sync.domain.usecase.ObserveSyncConfigUseCase
import com.nexus.sync.domain.usecase.PerformTwoWaySyncUseCase
import com.nexus.sync.domain.usecase.SaveInventoryItemUseCase
import com.nexus.sync.domain.usecase.UpdateSyncConfigUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val getInventoryItemsUseCase: GetInventoryItemsUseCase,
    private val saveInventoryItemUseCase: SaveInventoryItemUseCase,
    private val deleteInventoryItemUseCase: DeleteInventoryItemUseCase,
    private val performTwoWaySyncUseCase: PerformTwoWaySyncUseCase,
    private val observeSyncConfigUseCase: ObserveSyncConfigUseCase,
    private val updateSyncConfigUseCase: UpdateSyncConfigUseCase,
    private val observePendingSyncCountUseCase: ObservePendingSyncCountUseCase,
    private val syncScheduler: WorkManagerSyncScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<InventoryUiEffect>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val uiEffect: SharedFlow<InventoryUiEffect> = _uiEffect.asSharedFlow()

    private val searchQueryFlow = MutableStateFlow("")
    private val activeFilterFlow = MutableStateFlow(InventoryFilter.ALL)

    init {
        observeItemsAndFilters()
        observePendingCount()
        observeSyncConfig()
    }

    private fun observeItemsAndFilters() {
        combine(
            getInventoryItemsUseCase(),
            searchQueryFlow,
            activeFilterFlow
        ) { rawItems, query, filter ->
            val filtered = rawItems.filter { item ->
                val matchesQuery = query.isBlank() ||
                    item.name.contains(query, ignoreCase = true) ||
                    item.sku.contains(query, ignoreCase = true) ||
                    item.category.contains(query, ignoreCase = true)

                val matchesFilter = when (filter) {
                    InventoryFilter.ALL -> true
                    InventoryFilter.PENDING_SYNC -> item.syncStatus == SyncStatus.PENDING_SYNC
                    InventoryFilter.LOW_STOCK -> item.isLowStock
                    InventoryFilter.SYNCED -> item.syncStatus == SyncStatus.SYNCED
                }

                matchesQuery && matchesFilter
            }.map { InventoryItemUiModel.fromDomain(it) }

            filtered to rawItems.size
        }.onEach { (uiItems, total) ->
            _uiState.update {
                it.copy(
                    items = uiItems.toImmutableList(),
                    totalCount = total
                )
            }
        }.launchIn(viewModelScope)
    }

    private fun observePendingCount() {
        observePendingSyncCountUseCase().onEach { count ->
            _uiState.update { it.copy(pendingSyncCount = count) }
        }.launchIn(viewModelScope)
    }

    private fun observeSyncConfig() {
        observeSyncConfigUseCase().onEach { config ->
            _uiState.update { it.copy(syncConfig = config) }
            // Ensure periodic sync is scheduled
            if (config.autoSyncEnabled) {
                syncScheduler.schedulePeriodicSync(config.syncIntervalMinutes.toLong())
            }
        }.launchIn(viewModelScope)
    }

    fun onIntent(intent: InventoryIntent) {
        when (intent) {
            is InventoryIntent.TriggerSync -> triggerSync()
            is InventoryIntent.FilterSelected -> selectFilter(intent.filter)
            is InventoryIntent.SearchQueryChanged -> updateSearchQuery(intent.query)
            is InventoryIntent.SaveItem -> saveItem(intent)
            is InventoryIntent.DeleteItem -> deleteItem(intent.id)
            is InventoryIntent.OpenAddEditSheet -> _uiState.update {
                it.copy(isAddEditSheetOpen = true, editingItem = intent.item)
            }
            is InventoryIntent.DismissAddEditSheet -> _uiState.update {
                it.copy(isAddEditSheetOpen = false, editingItem = null)
            }
            is InventoryIntent.OpenConfigSheet -> _uiState.update { it.copy(isConfigSheetOpen = true) }
            is InventoryIntent.DismissConfigSheet -> _uiState.update { it.copy(isConfigSheetOpen = false) }
            is InventoryIntent.UpdateConflictStrategy -> updateConflictStrategy(intent.strategy)
            is InventoryIntent.UpdateSyncInterval -> updateSyncInterval(intent.minutes)
            is InventoryIntent.DismissError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun triggerSync() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, errorMessage = null) }

            val result = performTwoWaySyncUseCase()
            when (result) {
                is SyncResult.Success -> {
                    _uiState.update { it.copy(isSyncing = false) }
                    _uiEffect.emit(
                        InventoryUiEffect.ShowSnackbar(
                            "Sync completed: ${result.pushedCount} pushed, ${result.pulledCount} pulled, ${result.conflictsResolved} conflicts resolved."
                        )
                    )
                }
                is SyncResult.Failure -> {
                    _uiState.update {
                        it.copy(isSyncing = false, errorMessage = result.reason)
                    }
                    _uiEffect.emit(InventoryUiEffect.ShowSnackbar("Sync failed: ${result.reason}"))
                }
            }
        }
    }

    private fun selectFilter(filter: InventoryFilter) {
        activeFilterFlow.value = filter
        _uiState.update { it.copy(activeFilter = filter) }
    }

    private fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    private fun saveItem(intent: InventoryIntent.SaveItem) {
        viewModelScope.launch {
            val id = intent.id ?: "inv-${UUID.randomUUID().toString().take(8)}"
            val item = InventoryItem(
                id = id,
                sku = intent.sku.trim(),
                name = intent.name.trim(),
                category = intent.category.trim(),
                quantity = intent.quantity,
                unitPrice = intent.unitPrice,
                location = intent.location.trim(),
                lastModifiedEpoch = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING_SYNC
            )

            // Optimistic save
            saveInventoryItemUseCase(item)

            _uiState.update { it.copy(isAddEditSheetOpen = false, editingItem = null) }
            _uiEffect.emit(InventoryUiEffect.ShowSnackbar("Saved locally (${item.name}). Outbox enqueued."))
            _uiEffect.emit(InventoryUiEffect.ScrollToTop)

            // Trigger sync in background
            syncScheduler.triggerImmediateSync()
        }
    }

    private fun deleteItem(id: String) {
        viewModelScope.launch {
            deleteInventoryItemUseCase(id)
            _uiEffect.emit(InventoryUiEffect.ShowSnackbar("Item removed locally. Delete enqueued in outbox."))
            syncScheduler.triggerImmediateSync()
        }
    }

    private fun updateConflictStrategy(strategy: ConflictResolutionStrategy) {
        viewModelScope.launch {
            updateSyncConfigUseCase.updateConflictStrategy(strategy)
            _uiEffect.emit(InventoryUiEffect.ShowSnackbar("Conflict strategy updated to ${strategy.displayName}."))
        }
    }

    private fun updateSyncInterval(minutes: Int) {
        viewModelScope.launch {
            updateSyncConfigUseCase.updateSyncInterval(minutes)
            syncScheduler.schedulePeriodicSync(minutes.toLong())
            _uiEffect.emit(InventoryUiEffect.ShowSnackbar("Periodic sync set to every $minutes minutes."))
        }
    }
}
