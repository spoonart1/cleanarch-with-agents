package io.github.spoonart1.cleanarchwithagent.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.spoonart1.cleanarchwithagent.database.entity.ChecklistEntity
import io.github.spoonart1.cleanarchwithagent.database.entity.ChecklistItemEntity
import io.github.spoonart1.cleanarchwithagent.database.entity.OutboxEntity
import io.github.spoonart1.cleanarchwithagent.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {

    // --- Reads. The UI observes these; it never reads the network. ---

    @Query("SELECT * FROM checklists WHERE is_deleted = 0 ORDER BY updated_at DESC")
    fun observeChecklists(): Flow<List<ChecklistEntity>>

    @Query("SELECT * FROM checklists WHERE id = :id AND is_deleted = 0")
    fun observeChecklist(id: String): Flow<ChecklistEntity?>

    @Query(
        """
        SELECT * FROM checklist_items
        WHERE checklist_id = :checklistId AND is_deleted = 0
        ORDER BY updated_at ASC
        """,
    )
    fun observeItems(checklistId: String): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklists WHERE id = :id")
    suspend fun getChecklist(id: String): ChecklistEntity?

    @Query("SELECT * FROM checklist_items WHERE id = :id")
    suspend fun getItem(id: String): ChecklistItemEntity?

    @Query("SELECT * FROM checklists WHERE server_id = :serverId")
    suspend fun getChecklistByServerId(serverId: String): ChecklistEntity?

    @Query("SELECT * FROM checklist_items WHERE server_id = :serverId")
    suspend fun getItemByServerId(serverId: String): ChecklistItemEntity?

    // --- Plain writes, used by the sync engine when applying remote changes. ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChecklist(checklist: ChecklistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: ChecklistItemEntity)

    @Update
    suspend fun updateChecklist(checklist: ChecklistEntity)

    @Update
    suspend fun updateItem(item: ChecklistItemEntity)

    @Query("DELETE FROM checklists WHERE id = :id")
    suspend fun deleteChecklist(id: String)

    @Query("DELETE FROM checklist_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("UPDATE checklists SET sync_status = :status WHERE id = :id")
    suspend fun setChecklistSyncStatus(id: String, status: SyncStatus)

    @Query("UPDATE checklist_items SET sync_status = :status WHERE id = :id")
    suspend fun setItemSyncStatus(id: String, status: SyncStatus)

    // --- Local edits: row and outbox entry written together. ---

    /**
     * Writes the checklist and queues its outbox entry atomically.
     *
     * This pairing is the core offline-first guarantee. If the row were written
     * without the outbox entry the edit would never reach the server; if the
     * outbox entry were written without the row the user would not see their
     * own edit. [Transaction] makes both happen or neither.
     */
    @Transaction
    suspend fun upsertChecklistWithOutbox(
        checklist: ChecklistEntity,
        operation: OutboxEntity,
    ) {
        upsertChecklist(checklist)
        insertOutbox(operation)
    }

    @Transaction
    suspend fun upsertItemWithOutbox(
        item: ChecklistItemEntity,
        operation: OutboxEntity,
    ) {
        upsertItem(item)
        insertOutbox(operation)
    }

    /**
     * Marks a checklist deleted and queues the deletion.
     *
     * A soft delete, so the deletion still exists to be pushed. The row is
     * removed for real once the server confirms it.
     */
    @Transaction
    suspend fun softDeleteChecklistWithOutbox(
        id: String,
        updatedAt: Long,
        operation: OutboxEntity,
    ) {
        markChecklistDeleted(id, updatedAt)
        markItemsDeletedForChecklist(id, updatedAt)
        insertOutbox(operation)
    }

    @Query(
        """
        UPDATE checklists
        SET is_deleted = 1, updated_at = :updatedAt, sync_status = 'PENDING'
        WHERE id = :id
        """,
    )
    suspend fun markChecklistDeleted(id: String, updatedAt: Long)

    @Query(
        """
        UPDATE checklist_items
        SET is_deleted = 1, updated_at = :updatedAt, sync_status = 'PENDING'
        WHERE checklist_id = :checklistId
        """,
    )
    suspend fun markItemsDeletedForChecklist(checklistId: String, updatedAt: Long)

    /**
     * Declared here so the transactional methods above can insert into the
     * outbox within the same transaction. Room only guarantees atomicity across
     * calls made on one DAO instance.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutbox(operation: OutboxEntity)
}
