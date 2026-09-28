package io.github.spoonart1.cleanarchwithagent.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.spoonart1.cleanarchwithagent.model.SyncStatus

@Entity(
    tableName = "checklists",
    indices = [Index(value = ["server_id"], unique = true)],
)
data class ChecklistEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** Null until the server has accepted this checklist. */
    @ColumnInfo(name = "server_id")
    val serverId: String? = null,

    @ColumnInfo(name = "title")
    val title: String,

    /** Epoch millis. Drives last-write-wins. */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus,

    /**
     * Soft delete. A deletion has to survive until the server confirms it, so
     * the row stays until its outbox entry is pushed.
     */
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
)

@Entity(
    tableName = "checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistEntity::class,
            parentColumns = ["id"],
            childColumns = ["checklist_id"],
            // Deleting a checklist removes its items in the same statement,
            // so the two can never disagree.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["checklist_id"]),
        Index(value = ["server_id"], unique = true),
    ],
)
data class ChecklistItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "server_id")
    val serverId: String? = null,

    @ColumnInfo(name = "checklist_id")
    val checklistId: String,

    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "is_done")
    val isDone: Boolean = false,

    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
)
