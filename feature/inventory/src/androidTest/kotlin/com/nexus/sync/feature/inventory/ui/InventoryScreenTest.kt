package com.nexus.sync.feature.inventory.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.nexus.sync.domain.model.SyncStatus
import com.nexus.sync.feature.inventory.presentation.InventoryItemUiModel
import com.nexus.sync.feature.inventory.ui.components.InventoryItemCard
import org.junit.Rule
import org.junit.Test

class InventoryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun inventoryItemCard_displays_sku_name_and_status() {
        val testItem = InventoryItemUiModel(
            id = "test-1",
            sku = "SKU-TEST-99",
            name = "Industrial Managed Switch",
            category = "Networking",
            quantity = 2,
            unitPrice = 599.99,
            location = "Rack Alpha",
            syncStatus = SyncStatus.PENDING_SYNC,
            isLowStock = true,
            formattedPrice = "$599.99"
        )

        composeTestRule.setContent {
            InventoryItemCard(
                item = testItem,
                onEditClick = {},
                onDeleteClick = {}
            )
        }

        composeTestRule.onNodeWithText("SKU-TEST-99").assertIsDisplayed()
        composeTestRule.onNodeWithText("Industrial Managed Switch").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pending Sync").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 in stock (LOW)").assertIsDisplayed()
        composeTestRule.onNodeWithText("$599.99").assertIsDisplayed()
    }
}
