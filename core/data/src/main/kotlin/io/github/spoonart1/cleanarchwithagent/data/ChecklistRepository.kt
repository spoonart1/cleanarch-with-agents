package io.github.spoonart1.cleanarchwithagent.data

import io.github.spoonart1.cleanarchwithagent.model.Checklist
import io.github.spoonart1.cleanarchwithagent.model.ChecklistItem
import io.github.spoonart1.cleanarchwithagent.model.ChecklistSummary
import io.github.spoonart1.cleanarchwithagent.model.SyncState
import kotlinx.coroutines.flow.Flow

/**
 * The app's view of checklists.
 *
 * Every read is a [Flow] backed by Room, never a network call: the database is
 * the single source of truth, so the UI shows the same thing whether or not
 * there is a connection.
 *
 * Every write lands locally first and returns immediately, then asks the sync
 * engine to push in the background. A write never blocks on the network.
 */
interface ChecklistRepository {

    fun observeChecklists(): Flow<List<Checklist>>

    /** Checklists with their item counts, for a list screen. */
    fun observeChecklistSummaries(): Flow<List<ChecklistSummary>>

    fun observeChecklist(id: String): Flow<Checklist?>

    fun observeItems(checklistId: String): Flow<List<ChecklistItem>>

    /** Current sync progress, for the UI to display. */
    fun observeSyncState(): Flow<SyncState>

    /** @return the new checklist's local id. */
    suspend fun createChecklist(title: String): String

    suspend fun renameChecklist(id: String, title: String)

    suspend fun deleteChecklist(id: String)

    /** @return the new item's local id. */
    suspend fun addItem(checklistId: String, text: String): String

    suspend fun setItemDone(itemId: String, isDone: Boolean)

    suspend fun setItemNote(itemId: String, note: String?)

    suspend fun deleteItem(itemId: String)

    /** Asks for a sync now, e.g. from the settings screen's "sync now" button. */
    fun requestSync()
}
