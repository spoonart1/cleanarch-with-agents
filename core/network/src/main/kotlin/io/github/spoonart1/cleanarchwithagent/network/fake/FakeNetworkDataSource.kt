package io.github.spoonart1.cleanarchwithagent.network.fake

import io.github.spoonart1.cleanarchwithagent.common.Clock
import io.github.spoonart1.cleanarchwithagent.network.NetworkDataSource
import io.github.spoonart1.cleanarchwithagent.network.model.NetworkChecklist
import io.github.spoonart1.cleanarchwithagent.network.model.NetworkChecklistItem
import io.github.spoonart1.cleanarchwithagent.network.model.PushRequest
import io.github.spoonart1.cleanarchwithagent.network.model.PushResponse
import io.github.spoonart1.cleanarchwithagent.network.model.SyncResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An in-memory stand-in for the server, and the default for this template.
 *
 * It exists so the project runs on a fresh clone with no backend, and so the
 * network simulator can demonstrate offline-first behaviour. State lives in
 * memory only and is lost when the process dies — which is fine, because the
 * device database is the source of truth.
 *
 * It honours the same idempotency contract a real server would: see [push].
 */
@Singleton
class FakeNetworkDataSource @Inject constructor(
    private val simulator: NetworkSimulator,
    private val clock: Clock,
) : NetworkDataSource {

    private val checklists = ConcurrentHashMap<String, NetworkChecklist>()
    private val items = ConcurrentHashMap<String, NetworkChecklistItem>()

    /**
     * Results of operations already applied, keyed by operation id.
     *
     * This is what makes a replay safe. A real server would keep the same table
     * for the same reason: the client cannot tell "the server never got it"
     * apart from "the server got it and the response was lost", so it retries,
     * and the server must not apply the change twice.
     */
    private val appliedOperations = ConcurrentHashMap<String, PushResponse>()

    /** Serialises mutations so a concurrent push cannot interleave mid-operation. */
    private val mutex = Mutex()

    /**
     * Monotonic id sources, one per collection.
     *
     * Deliberately not `map.size + 1`: size falls when a record is removed, and
     * two creates that both read it before either writes see the same value. A
     * reused id silently overwrites the earlier record, which on the next pull
     * lands on the wrong local row. Only ever incremented, never reset — except
     * by [clear], which drops the records too.
     */
    private var nextChecklistNumber = 1L
    private var nextItemNumber = 1L

    override suspend fun pull(syncToken: String?): SyncResponse {
        simulator.simulate()

        val since = syncToken?.toLongOrNull() ?: 0L
        return SyncResponse(
            checklists = checklists.values.filter { it.updatedAt > since },
            items = items.values.filter { it.updatedAt > since },
            syncToken = clock.nowMillis().toString(),
        )
    }

    override suspend fun push(request: PushRequest): PushResponse {
        simulator.simulate()

        return mutex.withLock {
            // Idempotency: replaying an applied operation returns the original
            // result untouched. Checked inside the lock so two concurrent
            // replays cannot both get past it.
            appliedOperations[request.operationId]?.let { return@withLock it }

            val now = clock.nowMillis()
            val response = when {
                request.checklist != null -> applyChecklist(request, now)
                request.item != null -> applyItem(request, now)
                else -> error("Push request ${request.operationId} carried no payload")
            }

            appliedOperations[request.operationId] = response
            response
        }
    }

    private fun applyChecklist(request: PushRequest, now: Long): PushResponse {
        val incoming = requireNotNull(request.checklist)
        // The server assigns its own id on create; updates keep the one it gave out.
        val serverId = if (request.operation == OPERATION_CREATE) {
            "srv-checklist-${nextChecklistNumber++}"
        } else {
            incoming.id
        }

        checklists[serverId] = incoming.copy(
            id = serverId,
            updatedAt = now,
            isDeleted = request.operation == OPERATION_DELETE,
        )

        return PushResponse(
            operationId = request.operationId,
            serverId = serverId,
            updatedAt = now,
        )
    }

    private fun applyItem(request: PushRequest, now: Long): PushResponse {
        val incoming = requireNotNull(request.item)
        val serverId = if (request.operation == OPERATION_CREATE) {
            "srv-item-${nextItemNumber++}"
        } else {
            incoming.id
        }

        items[serverId] = incoming.copy(
            id = serverId,
            updatedAt = now,
            isDeleted = request.operation == OPERATION_DELETE,
        )

        return PushResponse(
            operationId = request.operationId,
            serverId = serverId,
            updatedAt = now,
        )
    }

    /** Test and demo hook: seed server-side state. */
    fun seedChecklist(checklist: NetworkChecklist) {
        checklists[checklist.id] = checklist
    }

    fun seedItem(item: NetworkChecklistItem) {
        items[item.id] = item
    }

    fun clear() {
        checklists.clear()
        items.clear()
        appliedOperations.clear()
        nextChecklistNumber = 1L
        nextItemNumber = 1L
    }

    companion object {
        const val OPERATION_CREATE = "CREATE"
        const val OPERATION_UPDATE = "UPDATE"
        const val OPERATION_DELETE = "DELETE"
    }
}
