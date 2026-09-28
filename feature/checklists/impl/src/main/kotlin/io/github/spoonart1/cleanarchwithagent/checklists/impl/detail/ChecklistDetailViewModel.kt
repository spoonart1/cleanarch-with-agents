package io.github.spoonart1.cleanarchwithagent.checklists.impl.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.spoonart1.cleanarchwithagent.checklists.api.ChecklistsRoutes
import io.github.spoonart1.cleanarchwithagent.data.ChecklistRepository
import io.github.spoonart1.cleanarchwithagent.model.ChecklistItem
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
class ChecklistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChecklistRepository,
) : ViewModel() {

    private val checklistId: String =
        checkNotNull(savedStateHandle[ChecklistsRoutes.ARG_CHECKLIST_ID]) {
            "ChecklistDetailViewModel requires a ${ChecklistsRoutes.ARG_CHECKLIST_ID} argument"
        }

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage

    val uiState: StateFlow<ChecklistDetailUiState> =
        combine(
            repository.observeChecklist(checklistId),
            repository.observeItems(checklistId),
            repository.observeSyncState(),
        ) { checklist, items, syncState ->
            if (checklist == null) {
                // Deleted while the screen was open, or an unknown id.
                ChecklistDetailUiState.Error("This checklist is no longer available")
            } else {
                ChecklistDetailUiState.Content(
                    title = checklist.title,
                    items = items.map { it.toUiModel() },
                    checklistSyncStatus = checklist.syncStatus,
                    syncState = syncState,
                )
            }
        }
            .catch { throwable ->
                emit(ChecklistDetailUiState.Error(throwable.message ?: "Could not load checklist"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = ChecklistDetailUiState.Loading,
            )

    fun addItem(text: String) {
        if (text.isBlank()) return
        runWrite("Could not add the item") {
            repository.addItem(checklistId, text.trim())
        }
    }

    fun setItemDone(itemId: String, isDone: Boolean) {
        runWrite("Could not update the item") {
            repository.setItemDone(itemId, isDone)
        }
    }

    fun setItemNote(itemId: String, note: String?) {
        runWrite("Could not save the note") {
            repository.setItemNote(itemId, note?.takeIf { it.isNotBlank() })
        }
    }

    fun deleteItem(itemId: String) {
        runWrite("Could not delete the item") {
            repository.deleteItem(itemId)
        }
    }

    fun onUserMessageShown() {
        _userMessage.value = null
    }

    /**
     * Runs a write, reporting failures as a message rather than a screen state.
     *
     * The list is still perfectly valid when a single write fails, so replacing
     * the whole screen with an error would throw away what the user can see.
     */
    private fun runWrite(failureMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _userMessage.value = e.message ?: failureMessage
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun ChecklistItem.toUiModel() = ChecklistItemUiModel(
    id = id,
    text = text,
    isDone = isDone,
    note = note,
    syncStatus = syncStatus,
)
