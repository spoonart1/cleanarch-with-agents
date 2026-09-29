package io.github.spoonart1.cleanarchwithagent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * End-to-end smoke tests: the app starts, and the two features can reach each
 * other through the multibound navigation graph.
 *
 * These are deliberately shallow. Screen behaviour is covered by the Compose
 * tests in each feature; what is unique here is that the real Hilt graph
 * resolves, WorkManager initialises with Hilt's worker factory, and the
 * navigation set assembles — none of which a JVM test exercises.
 *
 * Requires a device or emulator. Run with:
 *   ./gradlew :app:connectedDebugAndroidTest
 */
@HiltAndroidTest
class NavigationSmokeTest {

    // Order matters: the Hilt rule must run before the Compose rule so the
    // graph exists by the time the Activity is created.
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun appLaunchesToTheChecklistsScreen() {
        // If the Hilt graph or the navigation multibinding were broken, the
        // Activity would not reach a rendered start destination at all.
        composeRule.onNodeWithText("Checklists").assertIsDisplayed()
    }

    @Test
    fun canNavigateFromChecklistsToSettings() {
        composeRule.onNodeWithContentDescription("Settings").performClick()

        composeRule.onNodeWithText("Network simulator").assertIsDisplayed()
        composeRule.onNodeWithText("Sync now").assertIsDisplayed()
    }

    @Test
    fun canNavigateBackFromSettingsToChecklists() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Network simulator").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("Checklists").assertIsDisplayed()
    }

    @Test
    fun aCreatedChecklistSurvivesNavigatingAway() {
        // The point of offline-first: the checklist is written to Room before
        // any sync runs, so leaving the screen and coming back must still show
        // it even though the fake backend has not been contacted.
        composeRule.onNodeWithContentDescription("New checklist").performClick()
        composeRule.onNodeWithText("Title").performTextInput("Smoke test checklist")
        composeRule.onNodeWithText("Create").performClick()

        composeRule.onNodeWithText("Smoke test checklist").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("Smoke test checklist").assertIsDisplayed()
    }

    @Test
    fun openingAChecklistShowsItsDetailScreen() {
        composeRule.onNodeWithContentDescription("New checklist").performClick()
        composeRule.onNodeWithText("Title").performTextInput("Detail smoke test")
        composeRule.onNodeWithText("Create").performClick()

        composeRule.onNodeWithText("Detail smoke test").performClick()

        // The detail screen's add-item field is what distinguishes it from the
        // list; the title appears on both.
        composeRule.onNodeWithText("New item").assertIsDisplayed()
    }
}
