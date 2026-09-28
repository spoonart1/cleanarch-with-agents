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
    fun `checklists and their progress are shown`() {
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
    fun `the empty state is shown when there are no checklists`() {
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
    fun `an error state is shown instead of the list`() {
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
    fun `tapping a checklist reports its id`() {
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
    fun `creating a checklist passes the typed title through`() {
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
    fun `the sync indicator shows the pending count`() {
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
