package io.github.spoonart1.cleanarchwithagent.sync

import io.github.spoonart1.cleanarchwithagent.database.dao.ChecklistDao
import io.github.spoonart1.cleanarchwithagent.database.dao.OutboxDao
import io.github.spoonart1.cleanarchwithagent.database.entity.ChecklistEntity
import io.github.spoonart1.cleanarchwithagent.database.entity.ChecklistItemEntity
import io.github.spoonart1.cleanarchwithagent.database.entity.OutboxEntity
import io.github.spoonart1.cleanarchwithagent.model.OutboxEntityType
import io.github.spoonart1.cleanarchwithagent.model.SyncStatus
import io.github.spoonart1.cleanarchwithagent.network.NetworkDataSource
import io.github.spoonart1.cleanarchwithagent.network.model.NetworkChecklist
import io.github.spoonart1.cleanarchwithagent.network.model.NetworkChecklistItem
import io.github.spoonart1.cleanarchwithagent.network.model.PushRequest
import io.github.spoonart1.cleanarchwithagent.network.model.PushResponse
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** The outcome of one sync pass. */
sealed interface SyncResult {
    data object Success : SyncResult

    /** Something failed; the worker should retry with backoff. */
    data class Retry(val reason: String) : SyncResult
}

/**
 * Drains the outbox, then applies remote changes.
 *
 * Push happens before pull on purpose: local work reaches the server first, so
 * the pull that follows sees the server's view of those same changes and can
 * mark them synced in one pass.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val checklistDao: ChecklistDao,
    private val outboxDao: OutboxDao,
    private val network: NetworkDataSource,
    private val tokenStore: SyncTokenStore,
    private val monitor: SyncStatusMonitor,
) {

    suspend fun sync(): SyncResult {
        monitor.onSyncStarted()

        // Push and pull are attempted independently. A failed push must not
        // skip the pull: otherwise a device that cannot push would never learn
        // about remote changes, and so would never detect a conflict — exactly
        // the situation where noticing one matters most.
        return try {
            val pushFailure = runPhase { pushPendingOperations() }
            val pullFailure = runPhase { pullRemoteChanges() }

            val failure = pushFailure ?: pullFailure
            if (failure == null) {
                monitor.onSyncFinished()
                SyncResult.Success
            } else {
                monitor.onSyncFailed(failure)
                SyncResult.Retry(failure)
            }
        } catch (e: CancellationException) {
            // Never swallowed: the caller is being cancelled and must be allowed
            // to unwind. Reset the indicator first so it does not stick on
            // "syncing" forever.
            monitor.onSyncFinished()
            throw e
        }
    }

    /**
     * Runs one half of a sync, returning the failure reason or null.
     *
     * [CancellationException] is deliberately rethrown rather than reported as
     * a sync failure — cancellation is not an error, and swallowing it would
     * break structured concurrency.
     */
    private suspend fun runPhase(block: suspend () -> Unit): String? =
        try {
            block()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.message ?: e::class.simpleName ?: "Unknown sync failure"
        }

    /**
     * Pushes queued operations oldest-first.
     *
     * An entry that fails is left in the outbox and retried on the next pass,
     * until [MAX_PUSH_ATTEMPTS]. After that the record is marked
     * [SyncStatus.FAILED] and the entry is dropped, so one permanently
     * unpushable change cannot block everything queued behind it.
     */
    private suspend fun pushPendingOperations() {
        for (operation in outboxDao.pendingOperations()) {
            try {
                push(operation)
                outboxDao.deleteByOperationId(operation.operationId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: e::class.simpleName
                outboxDao.recordFailure(operation.operationId, message)

                if (operation.attemptCount + 1 >= MAX_PUSH_ATTEMPTS) {
                    markFailed(operation)
                    outboxDao.deleteByOperationId(operation.operationId)
                    // Give up on this one but keep draining the queue.
                    continue
                }
                // Transient: stop here so ordering is preserved, and let the
                // worker's backoff decide when to try again.
                throw e
            }
        }
    }

    private suspend fun push(operation: OutboxEntity) {
        val request = buildRequest(operation) ?: return
        val response = network.push(request)

        // The server's id and timestamp are now authoritative for this record.
        when (operation.entityType) {
            OutboxEntityType.CHECKLIST -> markChecklistSynced(operation.entityId, response)
            OutboxEntityType.CHECKLIST_ITEM -> markItemSynced(operation.entityId, response)
        }
    }

    /** Adopts the server's id and timestamp for a pushed checklist. */
    private suspend fun markChecklistSynced(entityId: String, response: PushResponse) {
        checklistDao.getChecklist(entityId)?.let { local ->
            checklistDao.updateChecklist(
                local.copy(
                    serverId = response.serverId,
                    updatedAt = response.updatedAt,
                    syncStatus = SyncStatus.SYNCED,
                ),
            )
        }
    }

    /** The item counterpart of [markChecklistSynced]. */
    private suspend fun markItemSynced(entityId: String, response: PushResponse) {
        checklistDao.getItem(entityId)?.let { local ->
            checklistDao.updateItem(
                local.copy(
                    serverId = response.serverId,
                    updatedAt = response.updatedAt,
                    syncStatus = SyncStatus.SYNCED,
                ),
            )
        }
    }

    /**
     * Builds the wire request for an outbox entry.
     *
     * Returns null when the referenced row is gone — the change was superseded,
     * so the entry is simply dropped rather than failing the pass.
     */
    private suspend fun buildRequest(operation: OutboxEntity): PushRequest? =
        when (operation.entityType) {
            OutboxEntityType.CHECKLIST ->
                checklistDao.getChecklist(operation.entityId)?.let { local ->
                    PushRequest(
                        operationId = operation.operationId,
                        operation = operation.operationType.name,
                        checklist = local.toNetworkModel(),
                    )
                }

            OutboxEntityType.CHECKLIST_ITEM ->
                checklistDao.getItem(operation.entityId)?.let { local ->
                    PushRequest(
                        operationId = operation.operationId,
                        operation = operation.operationType.name,
                        item = local.toNetworkModel(),
                    )
                }
        }

    private suspend fun markFailed(operation: OutboxEntity) {
        when (operation.entityType) {
            OutboxEntityType.CHECKLIST ->
                checklistDao.setChecklistSyncStatus(operation.entityId, SyncStatus.FAILED)

            OutboxEntityType.CHECKLIST_ITEM ->
                checklistDao.setItemSyncStatus(operation.entityId, SyncStatus.FAILED)
        }
    }

    /**
     * Applies everything the server has changed since the last pull.
     *
     * The token is written only after every record has been applied. If the
     * process dies midway the same window is pulled again, which is safe
     * because applying a remote record twice is idempotent — and far better
     * than advancing the token past changes that were never stored.
     */
    private suspend fun pullRemoteChanges() {
        val response = network.pull(tokenStore.read())

        for (remote in response.checklists) {
            applyRemoteChecklist(remote)
        }
        for (remote in response.items) {
            applyRemoteItem(remote)
        }

        tokenStore.write(response.syncToken)
    }

    /**
     * Merges one remote checklist into the local database.
     *
     * The rule that matters: a remote change never silently overwrites a local
     * change that is still pending. If the record has unpushed work, the local
     * version is kept and flagged [SyncStatus.CONFLICT] instead — the user's own
     * edit stays on screen, and the conflict is visible rather than lost.
     *
     * Otherwise last-write-wins on [updatedAt].
     */
    private suspend fun applyRemoteChecklist(remote: NetworkChecklist) {
        val local = checklistDao.getChecklistByServerId(remote.id)

        if (local == null) {
            checklistDao.upsertChecklist(remote.toEntity())
            return
        }

        if (hasPendingWork(OutboxEntityType.CHECKLIST, local.id)) {
            checklistDao.setChecklistSyncStatus(local.id, SyncStatus.CONFLICT)
            return
        }

        // Last-write-wins, now that the pending-work case is ruled out above.
        if (remote.updatedAt <= local.updatedAt) return
        overwriteChecklist(local, remote)
    }

    /** Takes the remote version of a checklist that has no unpushed local work. */
    private suspend fun overwriteChecklist(local: ChecklistEntity, remote: NetworkChecklist) {
        checklistDao.updateChecklist(
            local.copy(
                title = remote.title,
                updatedAt = remote.updatedAt,
                isDeleted = remote.isDeleted,
                syncStatus = SyncStatus.SYNCED,
            ),
        )
    }

    /** The item counterpart of [applyRemoteChecklist]; the same three rules apply. */
    private suspend fun applyRemoteItem(remote: NetworkChecklistItem) {
        val local = checklistDao.getItemByServerId(remote.id)

        if (local == null) {
            insertRemoteItem(remote)
            return
        }

        if (hasPendingWork(OutboxEntityType.CHECKLIST_ITEM, local.id)) {
            checklistDao.setItemSyncStatus(local.id, SyncStatus.CONFLICT)
            return
        }

        if (remote.updatedAt <= local.updatedAt) return
        overwriteItem(local, remote)
    }

    /** The item counterpart of [overwriteChecklist]. */
    private suspend fun overwriteItem(
        local: ChecklistItemEntity,
        remote: NetworkChecklistItem,
    ) {
        checklistDao.updateItem(
            local.copy(
                text = remote.text,
                isDone = remote.isDone,
                note = remote.note,
                updatedAt = remote.updatedAt,
                isDeleted = remote.isDeleted,
                syncStatus = SyncStatus.SYNCED,
            ),
        )
    }

    /**
     * Stores an item the device has not seen before.
     *
     * Skipped when the parent checklist is not present locally: the foreign key
     * would reject the row. The item arrives on a later pass, once its parent
     * has been pulled.
     */
    private suspend fun insertRemoteItem(remote: NetworkChecklistItem) {
        val parent = checklistDao.getChecklistByServerId(remote.checklistId) ?: return
        checklistDao.upsertItem(remote.toEntity(localChecklistId = parent.id))
    }

    private suspend fun hasPendingWork(type: OutboxEntityType, entityId: String): Boolean =
        outboxDao.hasPendingFor(type.name, entityId)

    companion object {
        /**
         * After this many failures a change stops being retried and is marked
         * FAILED. It stays on the device and visible; it simply stops blocking
         * the queue, since operations are pushed oldest-first.
         */
        const val MAX_PUSH_ATTEMPTS = 5
    }
}

// --- Mapping between database and wire models. ---

private fun ChecklistEntity.toNetworkModel() = NetworkChecklist(
    // Send the server id when there is one; on create the server assigns it.
    id = serverId ?: id,
    title = title,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
)

private fun ChecklistItemEntity.toNetworkModel() = NetworkChecklistItem(
    id = serverId ?: id,
    checklistId = checklistId,
    text = text,
    isDone = isDone,
    note = note,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
)

private fun NetworkChecklist.toEntity() = ChecklistEntity(
    id = id,
    serverId = id,
    title = title,
    updatedAt = updatedAt,
    syncStatus = SyncStatus.SYNCED,
    isDeleted = isDeleted,
)

private fun NetworkChecklistItem.toEntity(localChecklistId: String) = ChecklistItemEntity(
    id = id,
    serverId = id,
    checklistId = localChecklistId,
    text = text,
    isDone = isDone,
    note = note,
    updatedAt = updatedAt,
    syncStatus = SyncStatus.SYNCED,
    isDeleted = isDeleted,
)
