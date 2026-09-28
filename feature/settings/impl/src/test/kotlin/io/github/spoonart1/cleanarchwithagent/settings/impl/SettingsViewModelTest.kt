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
    fun `starts in normal network mode`() = runTest {
        val viewModel = SettingsViewModel(repository, simulator)

        viewModel.uiState.test {
            assertEquals(NetworkMode.NORMAL, awaitItem().networkMode)
        }
    }

    @Test
    fun `switching to offline is reflected in the state and the simulator`() = runTest {
        val viewModel = SettingsViewModel(repository, simulator)

        viewModel.uiState.test {
            skipItems(1)

            viewModel.setNetworkMode(NetworkMode.OFFLINE)

            assertEquals(NetworkMode.OFFLINE, awaitItem().networkMode)
            assertEquals(
                "the simulator itself must change, not just the UI",
                NetworkMode.OFFLINE,
                simulator.mode.value,
            )
        }
    }

    @Test
    fun `the pending count comes from the sync state`() = runTest {
        repository.setSyncState(SyncState.Idle(pendingCount = 7))
        val viewModel = SettingsViewModel(repository, simulator)

        viewModel.uiState.test {
            skipItems(1)
            assertEquals(7, awaitItem().pendingCount)
        }
    }

    @Test
    fun `isSyncing is true only while a sync is running`() = runTest {
        repository.setSyncState(SyncState.Syncing(pendingCount = 1))
        val viewModel = SettingsViewModel(repository, simulator)

        viewModel.uiState.test {
            skipItems(1)
            assertTrue(awaitItem().isSyncing)

            repository.setSyncState(SyncState.Idle(pendingCount = 0))

            assertFalse(awaitItem().isSyncing)
        }
    }

    @Test
    fun `sync now asks the repository to sync`() = runTest {
        val viewModel = SettingsViewModel(repository, simulator)

        viewModel.syncNow()

        assertEquals(1, repository.syncRequestCount)
    }

    @Test
    fun `an error sync state is carried through`() = runTest {
        repository.setSyncState(SyncState.Error(message = "offline", pendingCount = 2))
        val viewModel = SettingsViewModel(repository, simulator)

        viewModel.uiState.test {
            skipItems(1)
            val state = awaitItem()
            assertEquals(SyncState.Error("offline", pendingCount = 2), state.syncState)
            assertEquals(2, state.pendingCount)
        }
    }
}
