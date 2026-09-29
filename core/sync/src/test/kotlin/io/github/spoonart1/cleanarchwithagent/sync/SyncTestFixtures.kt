package io.github.spoonart1.cleanarchwithagent.sync

import io.github.spoonart1.cleanarchwithagent.database.entity.ChecklistEntity
import io.github.spoonart1.cleanarchwithagent.database.entity.ChecklistItemEntity
import io.github.spoonart1.cleanarchwithagent.database.entity.OutboxEntity
import io.github.spoonart1.cleanarchwithagent.model.OutboxEntityType
import io.github.spoonart1.cleanarchwithagent.model.OutboxOperationType
import io.github.spoonart1.cleanarchwithagent.model.SyncStatus
import io.github.spoonart1.cleanarchwithagent.network.NetworkDataSource
import io.github.spoonart1.cleanarchwithagent.network.model.PushRequest
import io.github.spoonart1.cleanarchwithagent.network.model.PushResponse
import io.github.spoonart1.cleanarchwithagent.network.model.SyncResponse
import java.io.IOException

/**
 * A network stand-in the tests can steer.
 *
 * A fake rather than a mock: it records what it was asked to do and can be told
 * to fail, which is all these tests need. See rule 5 in CLAUDE.md.
 */
internal class FakeNetwork : NetworkDataSource {

    /** Requests received, in order. Lets a test assert idempotent replay. */
    val pushed = mutableListOf<PushRequest>()

    /** What [pull] should return. */
    var pullResponse = SyncResponse(syncToken = "token-1")

    /** When set, every call throws this instead of succeeding. */
    var failWith: Exception? = null

    /** Fails only the first N pushes, then succeeds. Models a transient outage. */
    var failFirstPushes: Int = 0

    /** Operations the "server" has already applied, keyed by operation id. */
    private val applied = mutableMapOf<String, PushResponse>()

    override suspend fun pull(syncToken: String?): SyncResponse {
        failWith?.let { throw it }
        return pullResponse
    }

    override suspend fun push(request: PushRequest): PushResponse {
        failWith?.let { throw it }

        if (failFirstPushes > 0) {
            failFirstPushes--
            throw IOException("Simulated transient push failure")
        }

        pushed += request

        // Idempotent, like the real fake backend and like a real server: a
        // replayed operation id returns the original response.
        applied[request.operationId]?.let { return it }

        val response = PushResponse(
            operationId = request.operationId,
            serverId = "srv-${applied.size + 1}",
            updatedAt = SERVER_TIMESTAMP,
        )
        applied[request.operationId] = response
        return response
    }

    /** How many distinct operations the server actually applied. */
    val appliedCount: Int get() = applied.size

    companion object {
        const val SERVER_TIMESTAMP = 5_000L
    }
}

/** In-memory token store, so tests never touch SharedPreferences. */
internal class FakeSyncTokenStore(private var token: String? = null) : SyncTokenStore {
    override fun read(): String? = token
    override fun write(token: String) {
        this.token = token
    }
    override fun clear() {
        token = null
    }
}

// --- builders ---

internal fun checklistEntity(
    id: String,
    title: String = "Checklist",
    serverId: String? = null,
    updatedAt: Long = 1_000L,
    syncStatus: SyncStatus = SyncStatus.PENDING,
) = ChecklistEntity(
    id = id,
    serverId = serverId,
    title = title,
    updatedAt = updatedAt,
    syncStatus = syncStatus,
)

internal fun itemEntity(
    id: String,
    checklistId: String,
    text: String = "Item",
    serverId: String? = null,
    isDone: Boolean = false,
    note: String? = null,
    updatedAt: Long = 1_000L,
    syncStatus: SyncStatus = SyncStatus.PENDING,
) = ChecklistItemEntity(
    id = id,
    serverId = serverId,
    checklistId = checklistId,
    text = text,
    isDone = isDone,
    note = note,
    updatedAt = updatedAt,
    syncStatus = syncStatus,
)

internal fun outboxEntity(
    operationId: String,
    entityId: String,
    entityType: OutboxEntityType = OutboxEntityType.CHECKLIST,
    operationType: OutboxOperationType = OutboxOperationType.CREATE,
    createdAt: Long = 0,
    attemptCount: Int = 0,
) = OutboxEntity(
    operationId = operationId,
    entityType = entityType,
    entityId = entityId,
    operationType = operationType,
    createdAt = createdAt,
    attemptCount = attemptCount,
)
