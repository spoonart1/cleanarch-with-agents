package io.github.spoonart1.cleanarchwithagent.data

import io.github.spoonart1.cleanarchwithagent.common.Clock
import io.github.spoonart1.cleanarchwithagent.common.IdGenerator
import io.github.spoonart1.cleanarchwithagent.data.mapper.toDomain
import io.github.spoonart1.cleanarchwithagent.database.dao.ChecklistDao
import io.github.spoonart1.cleanarchwithagent.database.entity.ChecklistEntity
import io.github.spoonart1.cleanarchwithagent.database.entity.ChecklistItemEntity
import io.github.spoonart1.cleanarchwithagent.database.entity.OutboxEntity
import io.github.spoonart1.cleanarchwithagent.model.Checklist
import io.github.spoonart1.cleanarchwithagent.model.ChecklistItem
import io.github.spoonart1.cleanarchwithagent.model.ChecklistSummary
import io.github.spoonart1.cleanarchwithagent.model.OutboxEntityType
import io.github.spoonart1.cleanarchwithagent.model.OutboxOperationType
import io.github.spoonart1.cleanarchwithagent.model.SyncState
import io.github.spoonart1.cleanarchwithagent.model.SyncStatus
import io.github.spoonart1.cleanarchwithagent.sync.SyncScheduler
import io.github.spoonart1.cleanarchwithagent.sync.SyncStatusMonitor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The offline-first implementation.
 *
 * Each write follows the same three steps: build the row, write it together
 * with its outbox entry in one transaction, then ask for a sync. The write is
 * durable before the request returns, so the caller never waits on a network.
 */
@Singleton
class OfflineFirstChecklistRepository @Inject constructor(
    private val checklistDao: ChecklistDao,
    private val syncScheduler: SyncScheduler,
    private val syncStatusMonitor: SyncStatusMonitor,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
) : ChecklistRepository {

    override fun observeChecklists(): Flow<List<Checklist>> =
        checklistDao.observeChecklists().map { entities -> entities.map { it.toDomain() } }

    override fun observeChecklistSummaries(): Flow<List<ChecklistSummary>> =
        checklistDao.observeChecklistSummaries().map { rows -> rows.map { it.toDomain() } }

    override fun observeChecklist(id: String): Flow<Checklist?> =
        checklistDao.observeChecklist(id).map { it?.toDomain() }

    override fun observeItems(checklistId: String): Flow<List<ChecklistItem>> =
        checklistDao.observeItems(checklistId).map { entities -> entities.map { it.toDomain() } }

    override fun observeSyncState(): Flow<SyncState> = syncStatusMonitor.syncState

    override suspend fun createChecklist(title: String): String {
        val id = idGenerator.newId()
        val now = clock.nowMillis()

        writeChecklist(
            checklist = ChecklistEntity(
                id = id,
                title = title,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING,
            ),
            operationType = OutboxOperationType.CREATE,
            now = now,
        )
        return id
    }

    override suspend fun renameChecklist(id: String, title: String) {
        val existing = checklistDao.getChecklist(id) ?: return
        val now = clock.nowMillis()

        writeChecklist(
            checklist = existing.copy(
                title = title,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING,
            ),
            operationType = OutboxOperationType.UPDATE,
            now = now,
        )
    }

    override suspend fun deleteChecklist(id: String) {
        val now = clock.nowMillis()

        // Soft delete: the row stays until the server confirms, so the deletion
        // itself survives being made offline.
        checklistDao.softDeleteChecklistWithOutbox(
            id = id,
            updatedAt = now,
            operation = newOperation(
                entityType = OutboxEntityType.CHECKLIST,
                entityId = id,
                operationType = OutboxOperationType.DELETE,
                now = now,
            ),
        )

        syncScheduler.requestSync()
    }

    override suspend fun addItem(checklistId: String, text: String): String {
        val id = idGenerator.newId()
        val now = clock.nowMillis()

        writeItem(
            item = ChecklistItemEntity(
                id = id,
                checklistId = checklistId,
                text = text,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING,
            ),
            operationType = OutboxOperationType.CREATE,
            now = now,
        )
        return id
    }

    override suspend fun setItemDone(itemId: String, isDone: Boolean) {
        updateItem(itemId) { it.copy(isDone = isDone) }
    }

    override suspend fun setItemNote(itemId: String, note: String?) {
        updateItem(itemId) { it.copy(note = note) }
    }

    override suspend fun deleteItem(itemId: String) {
        updateItem(itemId, OutboxOperationType.DELETE) { it.copy(isDeleted = true) }
    }

    override fun requestSync() {
        syncScheduler.syncNow()
    }

    /**
     * Applies [change] to an item and queues the matching outbox entry.
     *
     * Shared by every item edit so the "write locally, queue, request sync"
     * sequence exists in exactly one place.
     */
    private suspend fun updateItem(
        itemId: String,
        operationType: OutboxOperationType = OutboxOperationType.UPDATE,
        change: (ChecklistItemEntity) -> ChecklistItemEntity,
    ) {
        val existing = checklistDao.getItem(itemId) ?: return
        val now = clock.nowMillis()

        writeItem(
            item = change(existing).copy(
                updatedAt = now,
                syncStatus = SyncStatus.PENDING,
            ),
            operationType = operationType,
            now = now,
        )
    }

    /**
     * Writes a checklist and its outbox entry in one transaction, then asks for
     * a sync.
     *
     * The transaction is the point: the row and the queued operation must land
     * together, or a crash between them would leave a change the server never
     * hears about.
     */
    private suspend fun writeChecklist(
        checklist: ChecklistEntity,
        operationType: OutboxOperationType,
        now: Long,
    ) {
        checklistDao.upsertChecklistWithOutbox(
            checklist = checklist,
            operation = newOperation(
                entityType = OutboxEntityType.CHECKLIST,
                entityId = checklist.id,
                operationType = operationType,
                now = now,
            ),
        )

        syncScheduler.requestSync()
    }

    /** The item counterpart of [writeChecklist]. */
    private suspend fun writeItem(
        item: ChecklistItemEntity,
        operationType: OutboxOperationType,
        now: Long,
    ) {
        checklistDao.upsertItemWithOutbox(
            item = item,
            operation = newOperation(
                entityType = OutboxEntityType.CHECKLIST_ITEM,
                entityId = item.id,
                operationType = operationType,
                now = now,
            ),
        )

        syncScheduler.requestSync()
    }

    private fun newOperation(
        entityType: OutboxEntityType,
        entityId: String,
        operationType: OutboxOperationType,
        now: Long,
    ) = OutboxEntity(
        // A fresh id per operation, so a retry of this exact change is
        // recognisable to the server as the same one.
        operationId = idGenerator.newId(),
        entityType = entityType,
        entityId = entityId,
        operationType = operationType,
        createdAt = now,
    )
}
