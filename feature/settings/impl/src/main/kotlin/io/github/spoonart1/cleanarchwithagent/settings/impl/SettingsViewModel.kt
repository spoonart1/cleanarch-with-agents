package io.github.spoonart1.cleanarchwithagent.settings.impl

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.spoonart1.cleanarchwithagent.data.ChecklistRepository
import io.github.spoonart1.cleanarchwithagent.model.SyncState
import io.github.spoonart1.cleanarchwithagent.network.fake.NetworkMode
import io.github.spoonart1.cleanarchwithagent.network.fake.NetworkSimulator
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@Immutable
data class SettingsUiState(
    val networkMode: NetworkMode = NetworkMode.NORMAL,
    val syncState: SyncState = SyncState.Idle(),
) {
    val pendingCount: Int get() = syncState.pendingCount
    val isSyncing: Boolean get() = syncState is SyncState.Syncing
}

/**
 * Backs the settings screen.
 *
 * The network-mode control is the template's demo switch: set it to OFFLINE,
 * make some edits, watch them queue, then set it back to NORMAL and watch them
 * sync. Nothing else in the app knows the simulator exists.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ChecklistRepository,
    private val networkSimulator: NetworkSimulator,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        combine(
            networkSimulator.mode,
            repository.observeSyncState(),
        ) { mode, syncState ->
            SettingsUiState(networkMode = mode, syncState = syncState)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SettingsUiState(),
        )

    fun setNetworkMode(mode: NetworkMode) {
        networkSimulator.setMode(mode)
    }

    fun syncNow() {
        repository.requestSync()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
