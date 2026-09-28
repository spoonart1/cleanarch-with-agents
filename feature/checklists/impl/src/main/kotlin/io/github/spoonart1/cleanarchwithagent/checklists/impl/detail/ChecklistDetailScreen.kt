package io.github.spoonart1.cleanarchwithagent.checklists.impl.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextDecoration
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

@Composable
fun ChecklistDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChecklistDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    ChecklistDetailScreen(
        uiState = uiState,
        userMessage = userMessage,
        onBack = onBack,
        onAddItem = viewModel::addItem,
        onItemCheckedChange = viewModel::setItemDone,
        onDeleteItem = viewModel::deleteItem,
        onUserMessageShown = viewModel::onUserMessageShown,
        modifier = modifier,
    )
}

@Composable
internal fun ChecklistDetailScreen(
    uiState: ChecklistDetailUiState,
    userMessage: String?,
    onBack: () -> Unit,
    onAddItem: (String) -> Unit,
    onItemCheckedChange: (String, Boolean) -> Unit,
    onDeleteItem: (String) -> Unit,
    onUserMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

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
                title = (uiState as? ChecklistDetailUiState.Content)?.title ?: "Checklist",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (uiState is ChecklistDetailUiState.Content) {
                        SyncStateIndicator(
                            syncState = uiState.syncState,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (uiState) {
                ChecklistDetailUiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                is ChecklistDetailUiState.Error ->
                    ErrorState(message = uiState.message)

                is ChecklistDetailUiState.Content ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        AddItemRow(onAddItem = onAddItem)

                        if (uiState.items.isEmpty()) {
                            EmptyState(
                                title = "No items yet",
                                description = "Add the first thing to check.",
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(uiState.items, key = { it.id }) { item ->
                                    ChecklistItemRow(
                                        item = item,
                                        onCheckedChange = { onItemCheckedChange(item.id, it) },
                                        onDelete = { onDeleteItem(item.id) },
                                    )
                                }
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun AddItemRow(onAddItem: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("New item") },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .testTag(DetailTestTags.NEW_ITEM_FIELD),
        )
        IconButton(
            onClick = {
                onAddItem(text)
                text = ""
            },
            enabled = text.isNotBlank(),
            modifier = Modifier.testTag(DetailTestTags.ADD_ITEM),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add item")
        }
    }
}

@Composable
private fun ChecklistItemRow(
    item: ChecklistItemUiModel,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        leadingContent = {
            Checkbox(checked = item.isDone, onCheckedChange = onCheckedChange)
        },
        headlineContent = {
            Text(
                text = item.text,
                textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
            )
        },
        supportingContent = item.note?.let { note ->
            { Text(text = note, style = MaterialTheme.typography.bodySmall) }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SyncStatusIcon(item.syncStatus)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete item")
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

internal object DetailTestTags {
    const val NEW_ITEM_FIELD = "new_item_field"
    const val ADD_ITEM = "add_item"
}

@Preview(showBackground = true)
@Composable
private fun ChecklistDetailPreview() {
    CleanArchTheme {
        ChecklistDetailScreen(
            uiState = ChecklistDetailUiState.Content(
                title = "Site survey",
                items = listOf(
                    ChecklistItemUiModel("1", "Check the gauge", true, "Read 4.2 bar", SyncStatus.SYNCED),
                    ChecklistItemUiModel("2", "Photograph the panel", false, null, SyncStatus.PENDING),
                    ChecklistItemUiModel("3", "Log the serial number", false, null, SyncStatus.CONFLICT),
                ),
                checklistSyncStatus = SyncStatus.PENDING,
                syncState = SyncState.Idle(pendingCount = 2),
            ),
            userMessage = null,
            onBack = {},
            onAddItem = {},
            onItemCheckedChange = { _, _ -> },
            onDeleteItem = {},
            onUserMessageShown = {},
        )
    }
}
