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
    fun `starts in loading and then emits content`() = runTest {
        val viewModel = ChecklistListViewModel(repository)

        viewModel.uiState.test {
            assertEquals(ChecklistListUiState.Loading, awaitItem())
            assertTrue(awaitItem() is ChecklistListUiState.Content)
        }
    }

    @Test
    fun `content carries each checklist's item counts`() = runTest {
        repository.setChecklists(testChecklist(id = "c1", title = "Site survey"))
        repository.setItems(
            testChecklistItem(id = "i1", checklistId = "c1", isDone = true),
            testChecklistItem(id = "i2", checklistId = "c1", isDone = false),
        )
        val viewModel = ChecklistListViewModel(repository)

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
    fun `content exposes the sync status of each checklist`() = runTest {
        repository.setChecklists(
            testChecklist(id = "c1", syncStatus = SyncStatus.PENDING),
        )
        val viewModel = ChecklistListViewModel(repository)

        viewModel.uiState.test {
            skipItems(1)
            val content = awaitItem() as ChecklistListUiState.Content

            assertEquals(SyncStatus.PENDING, content.checklists.first().syncStatus)
        }
    }

    @Test
    fun `content carries the global sync state`() = runTest {
        repository.setSyncState(SyncState.Syncing(pendingCount = 4))
        val viewModel = ChecklistListViewModel(repository)

        viewModel.uiState.test {
            skipItems(1)
            val content = awaitItem() as ChecklistListUiState.Content

            assertEquals(SyncState.Syncing(pendingCount = 4), content.syncState)
        }
    }

    @Test
    fun `creating a checklist adds it to the list`() = runTest {
        val viewModel = ChecklistListViewModel(repository)

        viewModel.uiState.test {
            skipItems(2) // Loading, then the initial empty Content

            viewModel.createChecklist("New checklist")

            val content = awaitItem() as ChecklistListUiState.Content
            assertEquals(listOf("New checklist"), content.checklists.map { it.title })
        }
    }

    @Test
    fun `a blank title is ignored rather than creating an unnamed checklist`() = runTest {
        val viewModel = ChecklistListViewModel(repository)

        viewModel.createChecklist("   ")

        assertEquals(0, repository.syncRequestCount)
    }

    @Test
    fun `a title is trimmed before it is stored`() = runTest {
        val viewModel = ChecklistListViewModel(repository)

        viewModel.uiState.test {
            skipItems(2)

            viewModel.createChecklist("  Padded  ")

            val content = awaitItem() as ChecklistListUiState.Content
            assertEquals("Padded", content.checklists.first().title)
        }
    }

    @Test
    fun `a failed write surfaces a message and leaves the list intact`() = runTest {
        repository.setChecklists(testChecklist(id = "c1", title = "Existing"))
        val viewModel = ChecklistListViewModel(repository)
        repository.failWith = IllegalStateException("database is full")

        viewModel.userMessage.test {
            assertEquals(null, awaitItem())

            viewModel.createChecklist("Doomed")

            assertEquals("database is full", awaitItem())
        }
    }

    @Test
    fun `dismissing the message clears it`() = runTest {
        val viewModel = ChecklistListViewModel(repository)
        repository.failWith = IllegalStateException("nope")

        viewModel.userMessage.test {
            skipItems(1)
            viewModel.createChecklist("Doomed")
            assertEquals("nope", awaitItem())

            viewModel.onUserMessageShown()

            assertEquals(null, awaitItem())
        }
    }

    @Test
    fun `deleting a checklist removes it from the list`() = runTest {
        repository.setChecklists(
            testChecklist(id = "c1", title = "Keep"),
            testChecklist(id = "c2", title = "Remove"),
        )
        val viewModel = ChecklistListViewModel(repository)

        viewModel.uiState.test {
            skipItems(2)

            viewModel.deleteChecklist("c2")

            val content = awaitItem() as ChecklistListUiState.Content
            assertEquals(listOf("Keep"), content.checklists.map { it.title })
        }
    }
}
