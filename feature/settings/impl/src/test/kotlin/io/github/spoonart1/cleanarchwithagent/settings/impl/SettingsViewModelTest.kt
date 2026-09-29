package io.github.spoonart1.cleanarchwithagent.settings.impl

import app.cash.turbine.test
import io.github.spoonart1.cleanarchwithagent.model.SyncState
import io.github.spoonart1.cleanarchwithagent.network.fake.NetworkMode
import io.github.spoonart1.cleanarchwithagent.network.fake.NetworkSimulator
import io.github.spoonart1.cleanarchwithagent.testing.FakeChecklistRepository
import io.github.spoonart1.cleanarchwithagent.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeChecklistRepository()
    private val simulator = NetworkSimulator()

    @Test
    fun `test uiState when newly constructed should start in normal network mode`() = runTest {
        // Given
        val viewModel = SettingsViewModel(repository, simulator)

        // When
        viewModel.uiState.test {
            // Then
            assertEquals(NetworkMode.NORMAL, awaitItem().networkMode)
        }
    }

    @Test
    fun `test setNetworkMode when switched to offline should update state and simulator`() = runTest {
        // Given
        val viewModel = SettingsViewModel(repository, simulator)

        viewModel.uiState.test {
            skipItems(1)

            // When
            viewModel.setNetworkMode(NetworkMode.OFFLINE)

            // Then
            assertEquals(NetworkMode.OFFLINE, awaitItem().networkMode)
            assertEquals(
                "the simulator itself must change, not just the UI",
                NetworkMode.OFFLINE,
                simulator.mode.value,
            )
        }
    }

    @Test
    fun `test uiState when sync state has pending work should expose the pending count`() = runTest {
        // Given
        repository.setSyncState(SyncState.Idle(pendingCount = 7))

        // When
        val viewModel = SettingsViewModel(repository, simulator)

        // Then
        viewModel.uiState.test {
            skipItems(1)
            assertEquals(7, awaitItem().pendingCount)
        }
    }

    @Test
    fun `test uiState when a sync starts and finishes should flip isSyncing`() = runTest {
        // Given
        repository.setSyncState(SyncState.Syncing(pendingCount = 1))
        val viewModel = SettingsViewModel(repository, simulator)

        viewModel.uiState.test {
            skipItems(1)

            // When
            assertTrue(awaitItem().isSyncing)

            // And
            repository.setSyncState(SyncState.Idle(pendingCount = 0))

            // Then
            assertFalse(awaitItem().isSyncing)
        }
    }

    @Test
    fun `test syncNow when called should ask the repository to sync`() = runTest {
        // Given
        val viewModel = SettingsViewModel(repository, simulator)

        // When
        viewModel.syncNow()

        // Then
        assertEquals(1, repository.syncRequestCount)
    }

    @Test
    fun `test uiState when the sync state is an error should carry it through`() = runTest {
        // Given
        repository.setSyncState(SyncState.Error(message = "offline", pendingCount = 2))

        // When
        val viewModel = SettingsViewModel(repository, simulator)

        // Then
        viewModel.uiState.test {
            skipItems(1)
            val state = awaitItem()
            assertEquals(SyncState.Error("offline", pendingCount = 2), state.syncState)
            assertEquals(2, state.pendingCount)
        }
    }
}
