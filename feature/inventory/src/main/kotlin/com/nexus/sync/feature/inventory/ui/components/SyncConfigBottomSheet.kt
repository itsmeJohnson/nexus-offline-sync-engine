package com.nexus.sync.feature.inventory.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.sync.domain.model.ConflictResolutionStrategy
import com.nexus.sync.domain.model.SyncConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncConfigBottomSheet(
    config: SyncConfig,
    onDismiss: () -> Unit,
    onStrategySelected: (ConflictResolutionStrategy) -> Unit,
    onIntervalSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                text = "Offline Sync Engine Config",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Backed by Proto DataStore (.proto schema)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Conflict Resolution Strategy
            Text(
                text = "Conflict Resolution Strategy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Option 1: Last-Write-Wins
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStrategySelected(ConflictResolutionStrategy.LAST_WRITE_WINS) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = config.conflictStrategy == ConflictResolutionStrategy.LAST_WRITE_WINS,
                    onClick = { onStrategySelected(ConflictResolutionStrategy.LAST_WRITE_WINS) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Last-Write-Wins (LWW)", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Compares local & remote timestamps. Highest epoch timestamp overwrites with incremented version.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Option 2: Server-Authoritative
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStrategySelected(ConflictResolutionStrategy.SERVER_AUTHORITATIVE) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = config.conflictStrategy == ConflictResolutionStrategy.SERVER_AUTHORITATIVE,
                    onClick = { onStrategySelected(ConflictResolutionStrategy.SERVER_AUTHORITATIVE) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Server-Authoritative", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Remote server state unconditionally wins in conflicts. Local pending outbox changes are evicted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sync Interval
            Text(
                text = "WorkManager Periodic Sync Interval",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                listOf(15, 30, 60).forEach { mins ->
                    FilterChip(
                        selected = config.syncIntervalMinutes == mins,
                        onClick = { onIntervalSelected(mins) },
                        label = { Text("${mins}m") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Metadata info
            Text(
                text = "Last Successful Sync:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            val dateText = if (config.lastSuccessfulSyncEpoch > 0) {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(config.lastSuccessfulSyncEpoch))
            } else {
                "Never (Initial state)"
            }
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Close")
            }
        }
    }
}
