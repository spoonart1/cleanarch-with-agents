package io.github.spoonart1.cleanarchwithagent.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.spoonart1.cleanarchwithagent.model.OutboxEntityType
import io.github.spoonart1.cleanarchwithagent.model.OutboxOperationType

/**
 * One pending change, queued for the server.
 *
 * [operationId] is the primary key and is sent to the server with the request.
 * A server that has already seen it returns the original result rather than
 * applying the change twice, which is what makes a retry safe after the app
 * dies between "server applied" and "local row marked synced".
 */
@Entity(
    tableName = "outbox",
    indices = [Index(value = ["entity_type", "entity_id"])],
)
data class OutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "operation_id")
    val operationId: String,

    @ColumnInfo(name = "entity_type")
    val entityType: OutboxEntityType,

    @ColumnInfo(name = "entity_id")
    val entityId: String,

    @ColumnInfo(name = "operation_type")
    val operationType: OutboxOperationType,

    /** Ordering key: entries are pushed oldest-first so causality is preserved. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,

    /** Diagnostic only — the reason the last attempt failed. */
    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
)
