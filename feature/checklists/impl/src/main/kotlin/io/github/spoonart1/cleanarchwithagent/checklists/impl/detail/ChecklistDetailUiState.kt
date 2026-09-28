package io.github.spoonart1.cleanarchwithagent.checklists.impl.detail

import androidx.compose.runtime.Immutable
import io.github.spoonart1.cleanarchwithagent.model.SyncState
import io.github.spoonart1.cleanarchwithagent.model.SyncStatus

/** One checklist item, as the detail screen needs it. */
@Immutable
data class ChecklistItemUiModel(
    val id: String,
    val text: String,
    val isDone: Boolean,
    val note: String?,
    val syncStatus: SyncStatus,
)

@Immutable
sealed interface ChecklistDetailUiState {

    data object Loading : ChecklistDetailUiState

    data class Content(
        val title: String,
        val items: List<ChecklistItemUiModel>,
        val checklistSyncStatus: SyncStatus,
        val syncState: SyncState,
    ) : ChecklistDetailUiState

    data class Error(val message: String) : ChecklistDetailUiState
}
