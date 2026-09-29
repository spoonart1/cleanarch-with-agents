package io.github.spoonart1.cleanarchwithagent.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.spoonart1.cleanarchwithagent.designsystem.theme.CleanArchTheme
import io.github.spoonart1.cleanarchwithagent.designsystem.theme.LocalSyncColors
import io.github.spoonart1.cleanarchwithagent.model.SyncState
import io.github.spoonart1.cleanarchwithagent.model.SyncStatus

/**
 * Per-record sync badge.
 *
 * Every icon carries a `contentDescription`: to a screen-reader user "pending"
 * is the entire point of the badge, so it must not be decorative.
 */
@Composable
fun SyncStatusIcon(
    status: SyncStatus,
    modifier: Modifier = Modifier,
) {
    val syncColors = LocalSyncColors.current

    val (icon: ImageVector, tint: Color, description: String) = when (status) {
        SyncStatus.SYNCED ->
            Triple(Icons.Default.CloudDone, syncColors.synced, "Synced")

        SyncStatus.PENDING ->
            Triple(Icons.Default.CloudQueue, syncColors.pending, "Waiting to sync")

        SyncStatus.FAILED ->
            Triple(Icons.Default.CloudOff, syncColors.failed, "Sync failed")

        SyncStatus.CONFLICT ->
            Triple(
                Icons.Default.ErrorOutline,
                syncColors.conflict,
                "Conflict: your version was kept",
            )
    }

    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = modifier.size(16.dp),
    )
}

/**
 * The global sync indicator, for a top bar.
 *
 * Shows the pending count whenever there is one, because "3 waiting" is more
 * reassuring than a spinner that might mean anything.
 */
@Composable
fun SyncStateIndicator(
    syncState: SyncState,
    modifier: Modifier = Modifier,
) {
    val syncColors = LocalSyncColors.current

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (syncState) {
            is SyncState.Syncing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Syncing",
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            is SyncState.Error -> {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Sync failed",
                    tint = syncColors.failed,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = pendingLabel(syncState.pendingCount) ?: "Sync failed",
                    style = MaterialTheme.typography.labelMedium,
                    color = syncColors.failed,
                )
            }

            is SyncState.Idle -> {
                val pending = pendingLabel(syncState.pendingCount)
                if (pending == null) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "All changes synced",
                        tint = syncColors.synced,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = pending,
                        tint = syncColors.pending,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = pending,
                        style = MaterialTheme.typography.labelMedium,
                        color = syncColors.pending,
                    )
                }
            }
        }
    }
}

private fun pendingLabel(count: Int): String? = when {
    count <= 0 -> null
    count == 1 -> "1 pending"
    else -> "$count pending"
}

@Preview(showBackground = true)
@Composable
private fun SyncStateIndicatorPreview() {
    CleanArchTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SyncStateIndicator(SyncState.Idle(pendingCount = 0))
            SyncStateIndicator(SyncState.Idle(pendingCount = 3))
            SyncStateIndicator(SyncState.Syncing(pendingCount = 2))
            SyncStateIndicator(SyncState.Error(message = "offline", pendingCount = 1))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SyncStatusIconPreview() {
    CleanArchTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SyncStatus.entries.forEach { SyncStatusIcon(it) }
        }
    }
}
