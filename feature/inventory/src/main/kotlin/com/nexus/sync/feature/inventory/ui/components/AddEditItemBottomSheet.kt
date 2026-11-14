package com.nexus.sync.feature.inventory.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nexus.sync.feature.inventory.presentation.InventoryItemUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditItemBottomSheet(
    editingItem: InventoryItemUiModel?,
    onDismiss: () -> Unit,
    onSave: (id: String?, sku: String, name: String, category: String, quantity: Int, price: Double, location: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var sku by remember(editingItem) { mutableStateOf(editingItem?.sku.orEmpty()) }
    var name by remember(editingItem) { mutableStateOf(editingItem?.name.orEmpty()) }
    var category by remember(editingItem) { mutableStateOf(editingItem?.category.orEmpty()) }
    var quantityStr by remember(editingItem) { mutableStateOf(editingItem?.quantity?.toString().orEmpty()) }
    var priceStr by remember(editingItem) { mutableStateOf(editingItem?.unitPrice?.toString().orEmpty()) }
    var location by remember(editingItem) { mutableStateOf(editingItem?.location.orEmpty()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (editingItem == null) "New Inventory Item" else "Edit Item",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = sku,
                onValueChange = { sku = it },
                label = { Text("SKU / Product Code") },
                placeholder = { Text("e.g. SKU-ROUTER-AX5") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Item Name") },
                placeholder = { Text("e.g. Enterprise WiFi 6 Router") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Category") },
                placeholder = { Text("e.g. Networking, Storage, Power") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = quantityStr,
                    onValueChange = { quantityStr = it },
                    label = { Text("Stock Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Unit Price ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Warehouse Location") },
                placeholder = { Text("e.g. Warehouse Alpha - Bay 3") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val qty = quantityStr.toIntOrNull() ?: 1
                    val price = priceStr.toDoubleOrNull() ?: 0.0
                    if (sku.isNotBlank() && name.isNotBlank()) {
                        onSave(editingItem?.id, sku, name, category, qty, price, location)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                enabled = sku.isNotBlank() && name.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (editingItem == null) "Create Item (Optimistic Offline)" else "Update Item")
            }
        }
    }
}
