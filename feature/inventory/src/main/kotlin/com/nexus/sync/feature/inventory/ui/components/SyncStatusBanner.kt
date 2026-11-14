package com.nexus.sync.feature.inventory.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.sync.domain.model.ConflictResolutionStrategy

@Composable
fun SyncStatusBanner(
    isSyncing: Boolean,
    pendingCount: Int,
    strategy: ConflictResolutionStrategy,
    onSyncClick: () -> Unit,
    onConfigClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSyncing -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
            pendingCount > 0 -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        label = "bannerColor"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (pendingCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE65100)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pendingCount.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Synced",
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                val statusText = when {
                    isSyncing -> "Synchronizing outbox with enterprise server..."
                    pendingCount > 0 -> "$pendingCount local changes pending sync (Offline Outbox)"
                    else -> "All inventory synced"
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onSyncClick,
                    enabled = !isSyncing
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync", fontSize = 12.sp)
                }
            }
        }
    }
}
