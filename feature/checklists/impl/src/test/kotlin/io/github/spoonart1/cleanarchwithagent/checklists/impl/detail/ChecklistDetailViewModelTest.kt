package io.github.spoonart1.cleanarchwithagent.checklists.impl.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.spoonart1.cleanarchwithagent.checklists.api.ChecklistsRoutes
import io.github.spoonart1.cleanarchwithagent.model.SyncState
import io.github.spoonart1.cleanarchwithagent.model.SyncStatus
import io.github.spoonart1.cleanarchwithagent.testing.FakeChecklistRepository
import io.github.spoonart1.cleanarchwithagent.testing.MainDispatcherRule
import io.github.spoonart1.cleanarchwithagent.testing.testChecklist
import io.github.spoonart1.cleanarchwithagent.testing.testChecklistItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChecklistDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeChecklistRepository()

    private fun viewModel(checklistId: String = CHECKLIST_ID) = ChecklistDetailViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(ChecklistsRoutes.ARG_CHECKLIST_ID to checklistId),
        ),
        repository = repository,
    )

    @Test
    fun `test uiState when first collected should start in loading`() = runTest {
        // Given
        repository.setChecklists(testChecklist(id = CHECKLIST_ID))

        // When
        val viewModel = viewModel()

        // Then
        viewModel.uiState.test {
            assertEquals(ChecklistDetailUiState.Loading, awaitItem())
        }
    }

    @Test
    fun `test uiState when the checklist exists should expose its title and items`() = runTest {
        // Given
        repository.setChecklists(testChecklist(id = CHECKLIST_ID, title = "Site survey"))

        // And it has two items
        repository.setItems(
            testChecklistItem(id = "i1", checklistId = CHECKLIST_ID, text = "Check the gauge"),
            testChecklistItem(id = "i2", checklistId = CHECKLIST_ID, text = "Log the serial"),
        )
        val viewModel = viewModel()

        // When
        viewModel.uiState.test {
            skipItems(1) // Loading

            // Then
            val content = awaitItem() as ChecklistDetailUiState.Content
            assertEquals("Site survey", content.title)
            assertEquals(listOf("Check the gauge", "Log the serial"), content.items.map { it.text })
        }
    }

    @Test
    fun `test uiState when the checklist is missing should expose an error`() = runTest {
        // Given no checklist with this id exists

        // When
        val viewModel = viewModel(checklistId = "does-not-exist")

        // Then
        viewModel.uiState.test {
            skipItems(1)
            assertTrue(awaitItem() is ChecklistDetailUiState.Error)
        }
    }

    @Test
    fun `test uiState when items belong to another checklist should not include them`() = runTest {
        // Given
        repository.setChecklists(testChecklist(id = CHECKLIST_ID))

        // And an item on a different checklist
        repository.setItems(
            testChecklistItem(id = "mine", checklistId = CHECKLIST_ID, text = "Mine"),
            testChecklistItem(id = "other", checklistId = "other-list", text = "Theirs"),
        )
        val viewModel = viewModel()

        // When
        viewModel.uiState.test {
            skipItems(1)

            // Then
            val content = awaitItem() as ChecklistDetailUiState.Content
            assertEquals(listOf("Mine"), content.items.map { it.text })
        }
    }

    @Test
    fun `test uiState when the checklist is pending should carry its sync status`() = runTest {
        // Given
        repository.setChecklists(
            testChecklist(id = CHECKLIST_ID, syncStatus = SyncStatus.PENDING),
        )
        val viewModel = viewModel()

        // When
        viewModel.uiState.test {
            skipItems(1)

            // Then
            val content = awaitItem() as ChecklistDetailUiState.Content
            assertEquals(SyncStatus.PENDING, content.checklistSyncStatus)
        }
    }

    @Test
    fun `test uiState when a sync is running should carry the global sync state`() = runTest {
        // Given
        repository.setChecklists(testChecklist(id = CHECKLIST_ID))
        repository.setSyncState(SyncState.Syncing(pendingCount = 3))
        val viewModel = viewModel()

        // When
        viewModel.uiState.test {
            skipItems(1)

            // Then
            val content = awaitItem() as ChecklistDetailUiState.Content
            assertEquals(SyncState.Syncing(pendingCount = 3), content.syncState)
        }
    }

    @Test
    fun `test addItem when given text should add it to the checklist`() = runTest {
        // Given
        repository.setChecklists(testChecklist(id = CHECKLIST_ID))
        val viewModel = viewModel()

        viewModel.uiState.test {
            skipItems(2) // Loading, then the initial empty Content

            // When
            viewModel.addItem("Check the gauge")

            // Then
            val content = awaitItem() as ChecklistDetailUiState.Content
            assertEquals(listOf("Check the gauge"), content.items.map { it.text })
        }
    }

    @Test
    fun `test addItem when the text is blank should not add anything`() = runTest {
        // Given
        repository.setChecklists(testChecklist(id = CHECKLIST_ID))
        val viewModel = viewModel()

        // When
        viewModel.addItem("   ")

        // Then
        assertEquals(0, repository.syncRequestCount)
    }

    @Test
    fun `test addItem when the text has padding should trim it`() = runTest {
        // Given
        repository.setChecklists(testChecklist(id = CHECKLIST_ID))
        val viewModel = viewModel()

        viewModel.uiState.test {
            skipItems(2)

            // When
            viewModel.addItem("  Padded  ")

            // Then
            val content = awaitItem() as ChecklistDetailUiState.Content
            assertEquals("Padded", content.items.single().text)
        }
    }

    @Test
    fun `test setItemDone when an item is ticked should mark it done`() = runTest {
        // Given
        repository.setChecklists(testChecklist(id = CHECKLIST_ID))
        repository.setItems(
            testChecklistItem(id = "i1", checklistId = CHECKLIST_ID, isDone = false),
        )
        val viewModel = viewModel()

        viewModel.uiState.test {
            skipItems(2)

            // When
            viewModel.setItemDone("i1", true)

            // Then
            val content = awaitItem() as ChecklistDetailUiState.Content
            assertTrue(content.items.single().isDone)
        }
    }

    @Test
    fun `test setItemNote when a note is given should store it`() = runTest {
        // Given
        repository.setChecklists(testChecklist(id = CHECKLIST_ID))
        repository.setItems(testChecklistItem(id = "i1", checklistId = CHECKLIST_ID))
        val viewModel = viewModel()

        viewModel.uiState.test {
            skipItems(2)

            // When
            viewModel.setItemNote("i1", "Read 4.2 bar")

            // Then
            val content = awaitItem() as ChecklistDetailUiState.Content
            assertEquals("Read 4.2 bar", content.items.single().note)
        }
    }

    @Test
    fun `test setItemNote when the note is blank should store null rather than an empty string`() =
        runTest {
            // Given an item that already has a note
            repository.setChecklists(testChecklist(id = CHECKLIST_ID))
            repository.setItems(
                testChecklistItem(id = "i1", checklistId = CHECKLIST_ID, note = "Existing"),
            )
            val viewModel = viewModel()

            viewModel.uiState.test {
                skipItems(2)

                // When
                viewModel.setItemNote("i1", "   ")

                // Then
                val content = awaitItem() as ChecklistDetailUiState.Content
                assertNull(content.items.single().note)
            }
        }

    @Test
    fun `test deleteItem when given an id should remove it from the list`() = runTest {
        // Given
        repository.setChecklists(testChecklist(id = CHECKLIST_ID))
        repository.setItems(
            testChecklistItem(id = "keep", checklistId = CHECKLIST_ID, text = "Keep"),
            testChecklistItem(id = "remove", checklistId = CHECKLIST_ID, text = "Remove"),
        )
        val viewModel = viewModel()

        viewModel.uiState.test {
            skipItems(2)

            // When
            viewModel.deleteItem("remove")

            // Then
            val content = awaitItem() as ChecklistDetailUiState.Content
            assertEquals(listOf("Keep"), content.items.map { it.text })
        }
    }

    @Test
    fun `test addItem when the write fails should surface a user message`() = runTest {
        // Given
        repository.setChecklists(testChecklist(id = CHECKLIST_ID))
        val viewModel = viewModel()

        // And the next write is set up to fail
        repository.failWith = IllegalStateException("database is full")

        viewModel.userMessage.test {
            assertNull(awaitItem())

            // When
            viewModel.addItem("Doomed")

            // Then
            assertEquals("database is full", awaitItem())
        }
    }

    @Test
    fun `test setItemDone when the write fails should surface a user message`() = runTest {
        // Given
        repository.setChecklists(testChecklist(id = CHECKLIST_ID))
        repository.setItems(testChecklistItem(id = "i1", checklistId = CHECKLIST_ID))
        val viewModel = viewModel()
        repository.failWith = IllegalStateException("write rejected")

        viewModel.userMessage.test {
            skipItems(1)

            // When
            viewModel.setItemDone("i1", true)

            // Then
            assertEquals("write rejected", awaitItem())
        }
    }

    @Test
    fun `test onUserMessageShown when a message is showing should clear it`() = runTest {
        // Given
        repository.setChecklists(testChecklist(id = CHECKLIST_ID))
        val viewModel = viewModel()
        repository.failWith = IllegalStateException("nope")

        viewModel.userMessage.test {
            skipItems(1)

            // And a message is showing
            viewModel.addItem("Doomed")
            assertEquals("nope", awaitItem())

            // When
            viewModel.onUserMessageShown()

            // Then
            assertNull(awaitItem())
        }
    }

    private companion object {
        const val CHECKLIST_ID = "checklist-1"
    }
}
