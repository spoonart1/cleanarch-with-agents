package io.github.spoonart1.cleanarchwithagent.settings.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.spoonart1.cleanarchwithagent.designsystem.component.CleanArchTopAppBar
import io.github.spoonart1.cleanarchwithagent.designsystem.component.SyncStateIndicator
import io.github.spoonart1.cleanarchwithagent.designsystem.theme.CleanArchTheme
import io.github.spoonart1.cleanarchwithagent.model.SyncState
import io.github.spoonart1.cleanarchwithagent.network.fake.NetworkMode

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        uiState = uiState,
        onBack = onBack,
        onNetworkModeChange = viewModel::setNetworkMode,
        onSyncNow = viewModel::syncNow,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onNetworkModeChange: (NetworkMode) -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CleanArchTopAppBar(
                title = "Settings",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SyncSection(
                syncState = uiState.syncState,
                pendingCount = uiState.pendingCount,
                isSyncing = uiState.isSyncing,
                onSyncNow = onSyncNow,
            )

            NetworkSimulatorSection(
                selectedMode = uiState.networkMode,
                onModeChange = onNetworkModeChange,
            )
        }
    }
}

@Composable
private fun SyncSection(
    syncState: SyncState,
    pendingCount: Int,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Sync", style = MaterialTheme.typography.titleMedium)

        SyncStateIndicator(syncState = syncState)

        Text(
            text = when (pendingCount) {
                0 -> "Everything is synced."
                1 -> "1 change waiting to sync."
                else -> "$pendingCount changes waiting to sync."
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(SettingsTestTags.PENDING_COUNT),
        )

        Button(
            onClick = onSyncNow,
            enabled = !isSyncing,
            modifier = Modifier.testTag(SettingsTestTags.SYNC_NOW),
        ) {
            Icon(Icons.Default.Sync, contentDescription = null)
            Text(text = "Sync now", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun NetworkSimulatorSection(
    selectedMode: NetworkMode,
    onModeChange: (NetworkMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Network simulator", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "The app talks to an in-memory fake backend. Switch to Offline, " +
                "make some edits, then switch back to Normal to watch them sync.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card {
            Column {
                NetworkMode.entries.forEach { mode ->
                    NetworkModeRow(
                        mode = mode,
                        selected = mode == selectedMode,
                        onSelect = { onModeChange(mode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkModeRow(
    mode: NetworkMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // selectable with Role.RadioButton makes the whole row one
            // accessible target rather than just the small circle.
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(SettingsTestTags.modeTag(mode)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RadioButton(selected = selected, onClick = null)
            Column {
                Text(text = mode.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val NetworkMode.label: String
    get() = when (this) {
        NetworkMode.NORMAL -> "Normal"
        NetworkMode.SLOW -> "Slow"
        NetworkMode.FLAKY -> "Flaky"
        NetworkMode.OFFLINE -> "Offline"
    }

private val NetworkMode.description: String
    get() = when (this) {
        NetworkMode.NORMAL -> "Responds promptly and succeeds."
        NetworkMode.SLOW -> "Succeeds, but slowly enough to see a loading state."
        NetworkMode.FLAKY -> "Fails about half the time, so retries are visible."
        NetworkMode.OFFLINE -> "Every request fails. Edits queue until you come back."
    }

internal object SettingsTestTags {
    const val SYNC_NOW = "sync_now"
    const val PENDING_COUNT = "pending_count"
    fun modeTag(mode: NetworkMode) = "network_mode_${mode.name}"
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    CleanArchTheme {
        SettingsScreen(
            uiState = SettingsUiState(
                networkMode = NetworkMode.OFFLINE,
                syncState = SyncState.Idle(pendingCount = 3),
            ),
            onBack = {},
            onNetworkModeChange = {},
            onSyncNow = {},
        )
    }
}
