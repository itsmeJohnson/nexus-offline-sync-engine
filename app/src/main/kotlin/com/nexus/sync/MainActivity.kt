package com.nexus.sync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nexus.sync.feature.inventory.ui.InventoryRoute
import com.nexus.sync.ui.theme.NexusSyncTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NexusSyncTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "inventory"
                ) {
                    composable("inventory") {
                        InventoryRoute()
                    }
                }
            }
        }
    }
}
