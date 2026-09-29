package io.github.spoonart1.cleanarchwithagent.checklists.impl.list

import app.cash.turbine.test
import io.github.spoonart1.cleanarchwithagent.model.SyncState
import io.github.spoonart1.cleanarchwithagent.model.SyncStatus
import io.github.spoonart1.cleanarchwithagent.testing.FakeChecklistRepository
import io.github.spoonart1.cleanarchwithagent.testing.MainDispatcherRule
import io.github.spoonart1.cleanarchwithagent.testing.testChecklist
import io.github.spoonart1.cleanarchwithagent.testing.testChecklistItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChecklistListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeChecklistRepository()

    @Test
    fun `test uiState when first collected should emit loading then content`() = runTest {
        // Given
        val viewModel = ChecklistListViewModel(repository)

        // When
        viewModel.uiState.test {
            // Then
            assertEquals(ChecklistListUiState.Loading, awaitItem())
            assertTrue(awaitItem() is ChecklistListUiState.Content)
        }
    }

    @Test
    fun `test uiState when a checklist has items should expose its item and done counts`() = runTest {
        // Given
        repository.setChecklists(testChecklist(id = "c1", title = "Site survey"))
        repository.setItems(
            testChecklistItem(id = "i1", checklistId = "c1", isDone = true),
            testChecklistItem(id = "i2", checklistId = "c1", isDone = false),
        )

        // When
        val viewModel = ChecklistListViewModel(repository)

        // Then
        viewModel.uiState.test {
            skipItems(1) // Loading
            val content = awaitItem() as ChecklistListUiState.Content

            assertEquals(1, content.checklists.size)
            assertEquals("Site survey", content.checklists.first().title)
            assertEquals(2, content.checklists.first().itemCount)
            assertEquals(1, content.checklists.first().doneCount)
        }
    }

    @Test
    fun `test uiState when a checklist is pending should expose its sync status`() = runTest {
        // Given
        repository.setChecklists(
            testChecklist(id = "c1", syncStatus = SyncStatus.PENDING),
        )

        // When
        val viewModel = ChecklistListViewModel(repository)

        // Then
        viewModel.uiState.test {
            skipItems(1)
            val content = awaitItem() as ChecklistListUiState.Content

            assertEquals(SyncStatus.PENDING, content.checklists.first().syncStatus)
        }
    }

    @Test
    fun `test uiState when a sync is running should carry the global sync state`() = runTest {
        // Given
        repository.setSyncState(SyncState.Syncing(pendingCount = 4))

        // When
        val viewModel = ChecklistListViewModel(repository)

        // Then
        viewModel.uiState.test {
            skipItems(1)
            val content = awaitItem() as ChecklistListUiState.Content

            assertEquals(SyncState.Syncing(pendingCount = 4), content.syncState)
        }
    }

    @Test
    fun `test createChecklist when given a title should add it to the list`() = runTest {
        // Given
        val viewModel = ChecklistListViewModel(repository)

        viewModel.uiState.test {
            skipItems(2) // Loading, then the initial empty Content

            // When
            viewModel.createChecklist("New checklist")

            // Then
            val content = awaitItem() as ChecklistListUiState.Content
            assertEquals(listOf("New checklist"), content.checklists.map { it.title })
        }
    }

    @Test
    fun `test createChecklist when the title is blank should not create anything`() = runTest {
        // Given
        val viewModel = ChecklistListViewModel(repository)

        // When
        viewModel.createChecklist("   ")

        // Then
        assertEquals(0, repository.syncRequestCount)
    }

    @Test
    fun `test createChecklist when the title has padding should trim it before storing`() = runTest {
        // Given
        val viewModel = ChecklistListViewModel(repository)

        viewModel.uiState.test {
            skipItems(2)

            // When
            viewModel.createChecklist("  Padded  ")

            // Then
            val content = awaitItem() as ChecklistListUiState.Content
            assertEquals("Padded", content.checklists.first().title)
        }
    }

    @Test
    fun `test createChecklist when the write fails should surface a user message`() = runTest {
        // Given
        repository.setChecklists(testChecklist(id = "c1", title = "Existing"))
        val viewModel = ChecklistListViewModel(repository)

        // And the next write is set up to fail
        repository.failWith = IllegalStateException("database is full")

        viewModel.userMessage.test {
            assertEquals(null, awaitItem())

            // When
            viewModel.createChecklist("Doomed")

            // Then
            assertEquals("database is full", awaitItem())
        }
    }

    @Test
    fun `test onUserMessageShown when a message is showing should clear it`() = runTest {
        // Given
        val viewModel = ChecklistListViewModel(repository)
        repository.failWith = IllegalStateException("nope")

        viewModel.userMessage.test {
            skipItems(1)

            // And a message is showing
            viewModel.createChecklist("Doomed")
            assertEquals("nope", awaitItem())

            // When
            viewModel.onUserMessageShown()

            // Then
            assertEquals(null, awaitItem())
        }
    }

    @Test
    fun `test deleteChecklist when given an id should remove it from the list`() = runTest {
        // Given
        repository.setChecklists(
            testChecklist(id = "c1", title = "Keep"),
            testChecklist(id = "c2", title = "Remove"),
        )
        val viewModel = ChecklistListViewModel(repository)

        viewModel.uiState.test {
            skipItems(2)

            // When
            viewModel.deleteChecklist("c2")

            // Then
            val content = awaitItem() as ChecklistListUiState.Content
            assertEquals(listOf("Keep"), content.checklists.map { it.title })
        }
    }
}
