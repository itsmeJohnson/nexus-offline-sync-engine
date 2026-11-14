package com.nexus.sync.feature.inventory.presentation

import androidx.compose.runtime.Immutable
import com.nexus.sync.domain.model.ConflictResolutionStrategy
import com.nexus.sync.domain.model.InventoryItem
import com.nexus.sync.domain.model.SyncConfig
import com.nexus.sync.domain.model.SyncStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.text.NumberFormat
import java.util.Locale

enum class InventoryFilter(val label: String) {
    ALL("All Items"),
    PENDING_SYNC("Pending Sync"),
    LOW_STOCK("Low Stock"),
    SYNCED("Synced")
}

@Immutable
data class InventoryItemUiModel(
    val id: String,
    val sku: String,
    val name: String,
    val category: String,
    val quantity: Int,
    val unitPrice: Double,
    val location: String,
    val syncStatus: SyncStatus,
    val isLowStock: Boolean,
    val formattedPrice: String,
    val version: Long = 1L
) {
    companion object {
        private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)

        fun fromDomain(domain: InventoryItem): InventoryItemUiModel {
            return InventoryItemUiModel(
                id = domain.id,
                sku = domain.sku,
                name = domain.name,
                category = domain.category,
                quantity = domain.quantity,
                unitPrice = domain.unitPrice,
                location = domain.location,
                syncStatus = domain.syncStatus,
                isLowStock = domain.isLowStock,
                formattedPrice = currencyFormatter.format(domain.unitPrice),
                version = domain.version
            )
        }
    }
}

@Immutable
data class InventoryUiState(
    val items: ImmutableList<InventoryItemUiModel> = persistentListOf(),
    val totalCount: Int = 0,
    val pendingSyncCount: Int = 0,
    val isSyncing: Boolean = false,
    val activeFilter: InventoryFilter = InventoryFilter.ALL,
    val searchQuery: String = "",
    val syncConfig: SyncConfig = SyncConfig(),
    val isConfigSheetOpen: Boolean = false,
    val isAddEditSheetOpen: Boolean = false,
    val editingItem: InventoryItemUiModel? = null,
    val errorMessage: String? = null
)

sealed interface InventoryIntent {
    data object TriggerSync : InventoryIntent
    data class FilterSelected(val filter: InventoryFilter) : InventoryIntent
    data class SearchQueryChanged(val query: String) : InventoryIntent
    data class SaveItem(
        val id: String?,
        val sku: String,
        val name: String,
        val category: String,
        val quantity: Int,
        val unitPrice: Double,
        val location: String
    ) : InventoryIntent
    data class DeleteItem(val id: String) : InventoryIntent
    data class OpenAddEditSheet(val item: InventoryItemUiModel? = null) : InventoryIntent
    data object DismissAddEditSheet : InventoryIntent
    data object OpenConfigSheet : InventoryIntent
    data object DismissConfigSheet : InventoryIntent
    data class UpdateConflictStrategy(val strategy: ConflictResolutionStrategy) : InventoryIntent
    data class UpdateSyncInterval(val minutes: Int) : InventoryIntent
    data object DismissError : InventoryIntent
}

sealed interface InventoryUiEffect {
    data class ShowSnackbar(val message: String) : InventoryUiEffect
    data object ScrollToTop : InventoryUiEffect
}
