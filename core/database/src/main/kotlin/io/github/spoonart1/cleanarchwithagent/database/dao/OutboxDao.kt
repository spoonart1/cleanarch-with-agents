package io.github.spoonart1.cleanarchwithagent.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.spoonart1.cleanarchwithagent.database.entity.OutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {

    /**
     * Oldest first, so operations reach the server in the order they happened.
     * A create must not overtake the update that follows it.
     */
    @Query("SELECT * FROM outbox ORDER BY created_at ASC")
    suspend fun pendingOperations(): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox")
    fun pendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: OutboxEntity)

    @Query("DELETE FROM outbox WHERE operation_id = :operationId")
    suspend fun deleteByOperationId(operationId: String)

    @Query(
        """
        UPDATE outbox
        SET attempt_count = attempt_count + 1, last_error = :error
        WHERE operation_id = :operationId
        """,
    )
    suspend fun recordFailure(operationId: String, error: String?)

    /** Whether a given entity still has unpushed work queued. */
    @Query(
        """
        SELECT COUNT(*) > 0 FROM outbox
        WHERE entity_type = :entityType AND entity_id = :entityId
        """,
    )
    suspend fun hasPendingFor(entityType: String, entityId: String): Boolean
}
