package io.github.spoonart1.cleanarchwithagent.testing

import io.github.spoonart1.cleanarchwithagent.data.ChecklistRepository
import io.github.spoonart1.cleanarchwithagent.model.Checklist
import io.github.spoonart1.cleanarchwithagent.model.ChecklistItem
import io.github.spoonart1.cleanarchwithagent.model.SyncState
import io.github.spoonart1.cleanarchwithagent.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * An in-memory [ChecklistRepository] for tests.
 *
 * A fake, not a mock: it behaves like the real thing, so a test asserts on
 * outcomes rather than on which methods were called. Set [failWith] to make
 * every write throw, which is how a ViewModel's error state gets exercised.
 */
class FakeChecklistRepository : ChecklistRepository {

    private val checklists = MutableStateFlow<List<Checklist>>(emptyList())
    private val items = MutableStateFlow<List<ChecklistItem>>(emptyList())
    private val syncState = MutableStateFlow<SyncState>(SyncState.Idle())

    /** When set, every suspending call throws this. */
    var failWith: Exception? = null

    /** Counts sync requests, so a test can assert a write asked for one. */
    var syncRequestCount: Int = 0
        private set

    private var nextId = 1

    override fun observeChecklists(): Flow<List<Checklist>> = checklists

    override fun observeChecklist(id: String): Flow<Checklist?> =
        checklists.map { all -> all.firstOrNull { it.id == id } }

    override fun observeItems(checklistId: String): Flow<List<ChecklistItem>> =
        items.map { all -> all.filter { it.checklistId == checklistId } }

    override fun observeSyncState(): Flow<SyncState> = syncState

    override suspend fun createChecklist(title: String): String {
        failWith?.let { throw it }
        val id = "checklist-${nextId++}"
        checklists.value += Checklist(id = id, title = title, updatedAt = 0)
        syncRequestCount++
        return id
    }

    override suspend fun renameChecklist(id: String, title: String) {
        failWith?.let { throw it }
        checklists.value = checklists.value.map {
            if (it.id == id) it.copy(title = title) else it
        }
        syncRequestCount++
    }

    override suspend fun deleteChecklist(id: String) {
        failWith?.let { throw it }
        checklists.value = checklists.value.filterNot { it.id == id }
        items.value = items.value.filterNot { it.checklistId == id }
        syncRequestCount++
    }

    override suspend fun addItem(checklistId: String, text: String): String {
        failWith?.let { throw it }
        val id = "item-${nextId++}"
        items.value += ChecklistItem(
            id = id,
            checklistId = checklistId,
            text = text,
            updatedAt = 0,
        )
        syncRequestCount++
        return id
    }

    override suspend fun setItemDone(itemId: String, isDone: Boolean) {
        failWith?.let { throw it }
        items.value = items.value.map {
            if (it.id == itemId) it.copy(isDone = isDone) else it
        }
        syncRequestCount++
    }

    override suspend fun setItemNote(itemId: String, note: String?) {
        failWith?.let { throw it }
        items.value = items.value.map {
            if (it.id == itemId) it.copy(note = note) else it
        }
        syncRequestCount++
    }

    override suspend fun deleteItem(itemId: String) {
        failWith?.let { throw it }
        items.value = items.value.filterNot { it.id == itemId }
        syncRequestCount++
    }

    override fun requestSync() {
        syncRequestCount++
    }

    // --- Test control ---

    fun setChecklists(vararg checklists: Checklist) {
        this.checklists.value = checklists.toList()
    }

    fun setItems(vararg items: ChecklistItem) {
        this.items.value = items.toList()
    }

    fun setSyncState(state: SyncState) {
        syncState.value = state
    }

    fun setSyncStatus(checklistId: String, status: SyncStatus) {
        checklists.value = checklists.value.map {
            if (it.id == checklistId) it.copy(syncStatus = status) else it
        }
    }
}
