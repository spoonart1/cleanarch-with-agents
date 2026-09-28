package io.github.spoonart1.cleanarchwithagent.checklists.impl.list

import androidx.compose.runtime.Immutable
import io.github.spoonart1.cleanarchwithagent.model.SyncState
import io.github.spoonart1.cleanarchwithagent.model.SyncStatus

/**
 * A checklist as the list screen needs it.
 *
 * A UI-specific model rather than the domain type: the screen needs
 * [itemCount], which the domain `Checklist` has no reason to carry, and
 * `@Immutable` lets Compose skip recomposition when nothing changed.
 */
@Immutable
data class ChecklistUiModel(
    val id: String,
    val title: String,
    val itemCount: Int,
    val doneCount: Int,
    val syncStatus: SyncStatus,
)

/**
 * The single state the list screen renders.
 *
 * A sealed interface so "loading with content" or "error with content" cannot
 * be represented by accident — every state names exactly what is on screen.
 */
@Immutable
sealed interface ChecklistListUiState {

    data object Loading : ChecklistListUiState

    data class Content(
        val checklists: List<ChecklistUiModel>,
        val syncState: SyncState,
    ) : ChecklistListUiState

    data class Error(val message: String) : ChecklistListUiState
}
