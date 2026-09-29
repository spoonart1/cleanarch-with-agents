package io.github.spoonart1.cleanarchwithagent.checklists.impl.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.spoonart1.cleanarchwithagent.designsystem.component.CleanArchTopAppBar
import io.github.spoonart1.cleanarchwithagent.designsystem.component.EmptyState
import io.github.spoonart1.cleanarchwithagent.designsystem.component.ErrorState
import io.github.spoonart1.cleanarchwithagent.designsystem.component.SyncStateIndicator
import io.github.spoonart1.cleanarchwithagent.designsystem.component.SyncStatusIcon
import io.github.spoonart1.cleanarchwithagent.designsystem.theme.CleanArchTheme
import io.github.spoonart1.cleanarchwithagent.model.SyncState
import io.github.spoonart1.cleanarchwithagent.model.SyncStatus

/** Stateful: reads the ViewModel and delegates rendering. */
@Composable
fun ChecklistListScreen(
    onChecklistClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChecklistListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    ChecklistListScreen(
        uiState = uiState,
        userMessage = userMessage,
        onChecklistClick = onChecklistClick,
        onSettingsClick = onSettingsClick,
        onCreateChecklist = viewModel::createChecklist,
        onUserMessageShown = viewModel::onUserMessageShown,
        modifier = modifier,
    )
}

/** Stateless: everything it renders arrives as a parameter, so it is previewable and testable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChecklistListScreen(
    uiState: ChecklistListUiState,
    userMessage: String?,
    onChecklistClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onCreateChecklist: (String) -> Unit,
    onUserMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            onUserMessageShown()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CleanArchTopAppBar(
                title = "Checklists",
                actions = {
                    if (uiState is ChecklistListUiState.Content) {
                        SyncStateIndicator(
                            syncState = uiState.syncState,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag(TestTags.SYNC_INDICATOR),
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.testTag(TestTags.ADD_CHECKLIST),
            ) {
                Icon(Icons.Default.Add, contentDescription = "New checklist")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (uiState) {
                ChecklistListUiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                is ChecklistListUiState.Error ->
                    ErrorState(message = uiState.message)

                is ChecklistListUiState.Content ->
                    if (uiState.checklists.isEmpty()) {
                        EmptyState(
                            title = "No checklists yet",
                            description =
                                "Create one to get started. Everything works offline and " +
                                    "syncs when you are back online.",
                        )
                    } else {
                        ChecklistList(
                            checklists = uiState.checklists,
                            onChecklistClick = onChecklistClick,
                        )
                    }
            }
        }
    }

    if (showCreateDialog) {
        CreateChecklistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { title ->
                onCreateChecklist(title)
                showCreateDialog = false
            },
        )
    }
}

@Composable
private fun ChecklistList(
    checklists: List<ChecklistUiModel>,
    onChecklistClick: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // A stable key so Compose reuses rows rather than rebuilding the list
        // whenever one checklist's sync status changes.
        items(checklists, key = { it.id }) { checklist ->
            ChecklistRow(checklist = checklist, onClick = { onChecklistClick(checklist.id) })
        }
    }
}

@Composable
private fun ChecklistRow(
    checklist: ChecklistUiModel,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(checklist.title) },
        supportingContent = {
            Text("${checklist.doneCount} of ${checklist.itemCount} done")
        },
        trailingContent = { SyncStatusIcon(checklist.syncStatus) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

/** Test tags, named once so a UI test and the screen cannot drift apart. */
internal object TestTags {
    const val ADD_CHECKLIST = "add_checklist"
    const val SYNC_INDICATOR = "sync_indicator"
    const val CHECKLIST_TITLE_FIELD = "checklist_title_field"
    const val CONFIRM_CREATE = "confirm_create"
}

@Preview(showBackground = true)
@Composable
private fun ChecklistListContentPreview() {
    CleanArchTheme {
        ChecklistListScreen(
            uiState = ChecklistListUiState.Content(
                checklists = listOf(
                    ChecklistUiModel("1", "Site survey", 5, 3, SyncStatus.SYNCED),
                    ChecklistUiModel("2", "Safety check", 4, 0, SyncStatus.PENDING),
                    ChecklistUiModel("3", "Handover", 2, 2, SyncStatus.CONFLICT),
                ),
                syncState = SyncState.Idle(pendingCount = 2),
            ),
            userMessage = null,
            onChecklistClick = {},
            onSettingsClick = {},
            onCreateChecklist = {},
            onUserMessageShown = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChecklistListEmptyPreview() {
    CleanArchTheme {
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
}
