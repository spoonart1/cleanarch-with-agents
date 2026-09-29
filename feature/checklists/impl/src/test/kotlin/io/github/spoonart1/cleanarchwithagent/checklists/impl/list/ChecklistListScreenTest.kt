package io.github.spoonart1.cleanarchwithagent.checklists.impl.list

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.spoonart1.cleanarchwithagent.model.SyncState
import io.github.spoonart1.cleanarchwithagent.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Drives the stateless screen directly, so no ViewModel or Hilt graph is needed.
 *
 * Runs on Robolectric rather than a device, which keeps it in `./gradlew test`.
 */
@RunWith(RobolectricTestRunner::class)
class ChecklistListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `test ChecklistListContent when given checklists should show them with progress`() {
        composeRule.setContent {
            ChecklistListScreen(
                uiState = ChecklistListUiState.Content(
                    checklists = listOf(
                        ChecklistUiModel("1", "Site survey", itemCount = 5, doneCount = 3, syncStatus = SyncStatus.SYNCED),
                    ),
                    syncState = SyncState.Idle(),
                ),
                userMessage = null,
                onChecklistClick = {},
                onSettingsClick = {},
                onCreateChecklist = {},
                onUserMessageShown = {},
            )
        }

        composeRule.onNodeWithText("Site survey").assertIsDisplayed()
        composeRule.onNodeWithText("3 of 5 done").assertIsDisplayed()
    }

    @Test
    fun `test ChecklistListContent when there are no checklists should show the empty state`() {
        composeRule.setContent {
            ChecklistListScreen(
                uiState = ChecklistListUiState.Content(
                    checklists = emptyList(),
                    syncState = SyncState.Idle(),
                ),
                userMessage = null,
                onChecklistClick = {},
                onSettingsClick = {},
                onCreateChecklist = {},
                onUserMessageShown = {},
            )
        }

        composeRule.onNodeWithText("No checklists yet").assertIsDisplayed()
    }

    @Test
    fun `test ChecklistListContent when the state is an error should show it instead of the list`() {
        composeRule.setContent {
            ChecklistListScreen(
                uiState = ChecklistListUiState.Error("Could not load checklists"),
                userMessage = null,
                onChecklistClick = {},
                onSettingsClick = {},
                onCreateChecklist = {},
                onUserMessageShown = {},
            )
        }

        composeRule.onNodeWithText("Could not load checklists").assertIsDisplayed()
    }

    @Test
    fun `test ChecklistListContent when a checklist is tapped should report its id`() {
        var clickedId: String? = null
        composeRule.setContent {
            ChecklistListScreen(
                uiState = ChecklistListUiState.Content(
                    checklists = listOf(
                        ChecklistUiModel("c-42", "Site survey", 0, 0, SyncStatus.SYNCED),
                    ),
                    syncState = SyncState.Idle(),
                ),
                userMessage = null,
                onChecklistClick = { clickedId = it },
                onSettingsClick = {},
                onCreateChecklist = {},
                onUserMessageShown = {},
            )
        }

        composeRule.onNodeWithText("Site survey").performClick()

        assertEquals("c-42", clickedId)
    }

    @Test
    fun `test ChecklistListContent when a title is typed should pass it through on create`() {
        var createdTitle: String? = null
        composeRule.setContent {
            ChecklistListScreen(
                uiState = ChecklistListUiState.Content(emptyList(), SyncState.Idle()),
                userMessage = null,
                onChecklistClick = {},
                onSettingsClick = {},
                onCreateChecklist = { createdTitle = it },
                onUserMessageShown = {},
            )
        }

        composeRule.onNodeWithTag(TestTags.ADD_CHECKLIST).performClick()
        composeRule.onNodeWithTag(TestTags.CHECKLIST_TITLE_FIELD).performTextInput("Safety check")
        composeRule.onNodeWithTag(TestTags.CONFIRM_CREATE).performClick()

        assertEquals("Safety check", createdTitle)
    }

    @Test
    fun `test ChecklistListContent when changes are pending should show the count in the indicator`() {
        composeRule.setContent {
            ChecklistListScreen(
                uiState = ChecklistListUiState.Content(
                    checklists = emptyList(),
                    syncState = SyncState.Idle(pendingCount = 3),
                ),
                userMessage = null,
                onChecklistClick = {},
                onSettingsClick = {},
                onCreateChecklist = {},
                onUserMessageShown = {},
            )
        }

        composeRule.onNodeWithText("3 pending").assertIsDisplayed()
    }
}
