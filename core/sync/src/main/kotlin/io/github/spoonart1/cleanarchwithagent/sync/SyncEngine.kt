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

/** What to do with one outbox entry, decided before the network is touched. */
private sealed interface PushPlan {
    data class Send(val request: PushRequest) : PushPlan

    /** The row is gone; the change was superseded, so discard the entry. */
    data object Drop : PushPlan

    /** Not sendable yet — it depends on an operation still queued ahead of it. */
    data object Defer : PushPlan
}

/** Whether an outbox entry may now be cleared, or must stay queued. */
private enum class PushOutcome { SENT, DEFERRED }

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
            pushOne(operation)
        }
    }

    /**
     * Pushes one entry, clearing it from the outbox once it is safely sent.
     *
     * Rethrows when the failure is transient, which stops the drain so ordering
     * is preserved; returns normally when the entry has been dealt with, whether
     * that meant sending it, deferring it or abandoning it.
     */
    private suspend fun pushOne(operation: OutboxEntity) {
        try {
            if (push(operation) == PushOutcome.DEFERRED) {
                recordAttempt(operation, DEFERRED_ERROR)
                return
            }
            outboxDao.deleteByOperationId(operation.operationId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Give up on an exhausted entry but keep draining the queue;
            // otherwise let the worker's backoff decide when to try again.
            val abandoned = recordAttempt(operation, e.message ?: e::class.simpleName)
            if (!abandoned) throw e
        }
    }

    /**
     * Spends one of an entry's attempts, abandoning it once they run out.
     *
     * Shared by the two ways an entry can fail to go out. A deferral is normally
     * transient — the item is waiting for its parent to be pushed, a pass or two
     * later — but it can also be terminal: if the parent carries a server id the
     * server no longer recognises, the item would defer on every pass forever,
     * invisibly. Spending an attempt either way means such an entry eventually
     * surfaces as [SyncStatus.FAILED] rather than sitting in the outbox unnoticed.
     *
     * @return true if the entry was abandoned, false if it remains queued.
     */
    private suspend fun recordAttempt(operation: OutboxEntity, error: String?): Boolean {
        outboxDao.recordFailure(operation.operationId, error)

        val isExhausted = operation.attemptCount + 1 >= MAX_PUSH_ATTEMPTS
        if (isExhausted) {
            markFailed(operation)
            outboxDao.deleteByOperationId(operation.operationId)
        }
        return isExhausted
    }

    private suspend fun push(operation: OutboxEntity): PushOutcome {
        val request = when (val plan = planRequest(operation)) {
            PushPlan.Drop -> return PushOutcome.SENT
            PushPlan.Defer -> return PushOutcome.DEFERRED
            is PushPlan.Send -> plan.request
        }
        val response = network.push(request)

        // The server's id and timestamp are now authoritative for this record.
        when (operation.entityType) {
            OutboxEntityType.CHECKLIST -> markChecklistSynced(operation.entityId, response)
            OutboxEntityType.CHECKLIST_ITEM -> markItemSynced(operation.entityId, response)
        }
        return PushOutcome.SENT
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
     * Decides what to do with an outbox entry.
     *
     * [PushPlan.Drop] when the referenced row is gone — the change was
     * superseded, so the entry is discarded rather than failing the pass.
     */
    private suspend fun planRequest(operation: OutboxEntity): PushPlan =
        when (operation.entityType) {
            OutboxEntityType.CHECKLIST -> {
                val local = checklistDao.getChecklist(operation.entityId)
                if (local == null) {
                    PushPlan.Drop
                } else {
                    PushPlan.Send(
                        PushRequest(
                            operationId = operation.operationId,
                            operation = operation.operationType.name,
                            checklist = local.toNetworkModel(),
                        ),
                    )
                }
            }

            OutboxEntityType.CHECKLIST_ITEM -> planItemRequest(operation)
        }

    /**
     * The item counterpart of [planRequest].
     *
     * An item is only pushable once its parent has a server id, because the
     * request has to name the parent the way the server knows it. Until then the
     * entry is deferred rather than dropped, so the item follows on a later pass
     * once its parent's own push has completed.
     */
    private suspend fun planItemRequest(operation: OutboxEntity): PushPlan {
        val local = checklistDao.getItem(operation.entityId) ?: return PushPlan.Drop
        val parentServerId = checklistDao.getChecklist(local.checklistId)?.serverId
            ?: return PushPlan.Defer

        return PushPlan.Send(
            PushRequest(
                operationId = operation.operationId,
                operation = operation.operationType.name,
                item = local.toNetworkModel(parentServerId),
            ),
        )
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
     * has been pulled. [remote.checklistId] is matched against `serverId`, which
     * is why the push side must send the parent's server id and not its local
     * one — see [toNetworkModel].
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

        /** Recorded against a deferred entry, so a stuck one is diagnosable. */
        const val DEFERRED_ERROR = "Waiting for the parent checklist to be pushed"
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

/**
 * [parentServerId] is the id the *server* knows the parent by, not the local
 * one. Sending the local id would make the item unmatchable on the way back:
 * the pull resolves a parent by server id, so the lookup would miss and the
 * item would be dropped — and against a real backend it would leak device-local
 * ids into the wire format.
 */
private fun ChecklistItemEntity.toNetworkModel(parentServerId: String) = NetworkChecklistItem(
    id = serverId ?: id,
    checklistId = parentServerId,
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
