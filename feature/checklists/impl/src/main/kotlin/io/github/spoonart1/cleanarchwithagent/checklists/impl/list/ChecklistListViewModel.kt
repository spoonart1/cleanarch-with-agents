package io.github.spoonart1.cleanarchwithagent.checklists.impl.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.spoonart1.cleanarchwithagent.data.ChecklistRepository
import io.github.spoonart1.cleanarchwithagent.model.ChecklistSummary
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ChecklistListViewModel @Inject constructor(
    private val repository: ChecklistRepository,
) : ViewModel() {

    /** Set when a write fails; surfaced as a one-off message, not a screen state. */
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage

    val uiState: StateFlow<ChecklistListUiState> =
        combine(
            repository.observeChecklistSummaries(),
            repository.observeSyncState(),
        ) { summaries, syncState ->
            // Typed as the interface, not Content, so `catch` below can emit an
            // Error into the same flow.
            ChecklistListUiState.Content(
                checklists = summaries.map { it.toUiModel() },
                syncState = syncState,
            ) as ChecklistListUiState
        }
            .catch { throwable ->
                // A failure in the database flow is a screen-level error: there
                // is nothing to show. CancellationException never reaches here —
                // `catch` re-throws it rather than treating it as a failure.
                emit(ChecklistListUiState.Error(throwable.message ?: "Could not load checklists"))
            }
            .stateIn(
                scope = viewModelScope,
                // Keep collecting briefly across a configuration change, so a
                // rotation does not drop back to Loading and re-query.
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = ChecklistListUiState.Loading,
            )

    fun createChecklist(title: String) {
        if (title.isBlank()) return

        viewModelScope.launch {
            try {
                repository.createChecklist(title.trim())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _userMessage.value = e.message ?: "Could not create the checklist"
            }
        }
    }

    fun deleteChecklist(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteChecklist(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _userMessage.value = e.message ?: "Could not delete the checklist"
            }
        }
    }

    fun onUserMessageShown() {
        _userMessage.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun ChecklistSummary.toUiModel() = ChecklistUiModel(
    id = checklist.id,
    title = checklist.title,
    itemCount = itemCount,
    doneCount = doneCount,
    syncStatus = checklist.syncStatus,
)
