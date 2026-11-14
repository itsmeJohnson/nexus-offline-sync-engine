package com.nexus.sync.feature.inventory.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.sync.feature.inventory.presentation.InventoryFilter
import com.nexus.sync.feature.inventory.presentation.InventoryIntent
import com.nexus.sync.feature.inventory.presentation.InventoryUiEffect
import com.nexus.sync.feature.inventory.presentation.InventoryViewModel
import com.nexus.sync.feature.inventory.ui.components.AddEditItemBottomSheet
import com.nexus.sync.feature.inventory.ui.components.InventoryItemCard
import com.nexus.sync.feature.inventory.ui.components.SyncConfigBottomSheet
import com.nexus.sync.feature.inventory.ui.components.SyncStatusBanner
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun InventoryRoute(
    modifier: Modifier = Modifier,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(viewModel.uiEffect) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                is InventoryUiEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is InventoryUiEffect.ScrollToTop -> {
                    listState.animateScrollToItem(0)
                }
            }
        }
    }

    InventoryScreen(
        uiState = uiState,
        listState = listState,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::onIntent,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    uiState: com.nexus.sync.feature.inventory.presentation.InventoryUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    snackbarHostState: SnackbarHostState,
    onIntent: (InventoryIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    // 60 FPS Optimization: derivedStateOf avoids recomposing on every single scroll pixel
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Nexus Sync Engine",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.totalCount} Enterprise Items · Strategy: ${uiState.syncConfig.conflictStrategy.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onIntent(InventoryIntent.OpenConfigSheet) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Sync Config"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Smooth scroll to top FAB
                AnimatedVisibility(
                    visible = showScrollToTop,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = CircleShape
                    ) {
                        Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to top")
                    }
                }

                FloatingActionButton(
                    onClick = { onIntent(InventoryIntent.OpenAddEditSheet(null)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Item")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { onIntent(InventoryIntent.SearchQueryChanged(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Search by SKU, product name, or category...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InventoryFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = uiState.activeFilter == filter,
                        onClick = { onIntent(InventoryIntent.FilterSelected(filter)) },
                        label = { Text(filter.label) }
                    )
                }
            }

            // Sync Status Banner
            SyncStatusBanner(
                isSyncing = uiState.isSyncing,
                pendingCount = uiState.pendingSyncCount,
                strategy = uiState.syncConfig.conflictStrategy,
                onSyncClick = { onIntent(InventoryIntent.TriggerSync) },
                onConfigClick = { onIntent(InventoryIntent.OpenConfigSheet) }
            )

            // High-Performance LazyColumn
            if (uiState.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No items match current filter",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap + to add an offline-first inventory item",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(
                        items = uiState.items,
                        key = { item -> item.id },
                        contentType = { "inventory_card" }
                    ) { item ->
                        InventoryItemCard(
                            item = item,
                            onEditClick = { onIntent(InventoryIntent.OpenAddEditSheet(item)) },
                            onDeleteClick = { onIntent(InventoryIntent.DeleteItem(item.id)) }
                        )
                    }
                }
            }
        }
    }

    if (uiState.isAddEditSheetOpen) {
        AddEditItemBottomSheet(
            editingItem = uiState.editingItem,
            onDismiss = { onIntent(InventoryIntent.DismissAddEditSheet) },
            onSave = { id, sku, name, category, quantity, price, location ->
                onIntent(
                    InventoryIntent.SaveItem(
                        id = id,
                        sku = sku,
                        name = name,
                        category = category,
                        quantity = quantity,
                        unitPrice = price,
                        location = location
                    )
                )
            }
        )
    }

    if (uiState.isConfigSheetOpen) {
        SyncConfigBottomSheet(
            config = uiState.syncConfig,
            onDismiss = { onIntent(InventoryIntent.DismissConfigSheet) },
            onStrategySelected = { onIntent(InventoryIntent.UpdateConflictStrategy(it)) },
            onIntervalSelected = { onIntent(InventoryIntent.UpdateSyncInterval(it)) }
        )
    }
}
