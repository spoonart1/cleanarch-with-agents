package io.github.spoonart1.cleanarchwithagent.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.spoonart1.cleanarchwithagent.database.dao.ChecklistDao
import io.github.spoonart1.cleanarchwithagent.database.dao.OutboxDao
import io.github.spoonart1.cleanarchwithagent.database.entity.ChecklistEntity
import io.github.spoonart1.cleanarchwithagent.database.entity.ChecklistItemEntity
import io.github.spoonart1.cleanarchwithagent.database.entity.OutboxEntity
import io.github.spoonart1.cleanarchwithagent.model.OutboxEntityType
import io.github.spoonart1.cleanarchwithagent.model.OutboxOperationType
import io.github.spoonart1.cleanarchwithagent.model.SyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChecklistDaoTest {

    private lateinit var database: CleanArchDatabase
    private lateinit var checklistDao: ChecklistDao
    private lateinit var outboxDao: OutboxDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CleanArchDatabase::class.java,
        ).build()
        checklistDao = database.checklistDao()
        outboxDao = database.outboxDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `test observeChecklists when rows are inserted should emit them newest first`() = runTest {
        checklistDao.upsertChecklist(checklist(id = "1", title = "Older", updatedAt = 100))
        checklistDao.upsertChecklist(checklist(id = "2", title = "Newer", updatedAt = 200))

        val observed = checklistDao.observeChecklists().first()

        assertEquals(listOf("Newer", "Older"), observed.map { it.title })
    }

    @Test
    fun `test observeChecklists when a row is soft deleted should hide it`() = runTest {
        checklistDao.upsertChecklist(checklist(id = "1", title = "Visible"))
        checklistDao.upsertChecklist(
            checklist(id = "2", title = "Gone").copy(isDeleted = true),
        )

        val observed = checklistDao.observeChecklists().first()

        assertEquals(listOf("Visible"), observed.map { it.title })
    }

    @Test
    fun `test upsertChecklistWithOutbox when called should write the row and outbox entry together`() = runTest {
        checklistDao.upsertChecklistWithOutbox(
            checklist = checklist(id = "1", title = "Site survey"),
            operation = outbox(operationId = "op-1", entityId = "1"),
        )

        assertNotNull(checklistDao.getChecklist("1"))
        assertEquals(1, outboxDao.pendingOperations().size)
        assertEquals("op-1", outboxDao.pendingOperations().first().operationId)
    }

    @Test
    fun `test upsertChecklistWithOutbox when the transaction fails should write neither`() = runTest {
        // A foreign-key violation: the item references a checklist that does not
        // exist, so the insert fails. The outbox entry queued alongside it in the
        // same transaction must roll back too -- otherwise the app would try to
        // push a change to a record it never stored.
        val orphanItem = item(id = "i1", checklistId = "does-not-exist")

        val result = runCatching {
            checklistDao.upsertItemWithOutbox(
                item = orphanItem,
                operation = outbox(
                    operationId = "op-orphan",
                    entityId = "i1",
                    entityType = OutboxEntityType.CHECKLIST_ITEM,
                ),
            )
        }

        // Assert the insert really was rejected. Room leaves foreign-key
        // enforcement to SQLite's per-connection pragma, so without this check
        // the test would pass vacuously if constraints were ever off.
        assertTrue(
            "expected the orphan insert to fail, but it succeeded — " +
                "foreign keys are not being enforced, so this test proves nothing",
            result.isFailure,
        )
        assertNull(checklistDao.getItem("i1"))
        assertTrue(
            "outbox entry survived a rolled-back transaction",
            outboxDao.pendingOperations().none { it.operationId == "op-orphan" },
        )
    }

    @Test
    fun `test softDeleteChecklist when called should also mark its items deleted`() = runTest {
        checklistDao.upsertChecklist(checklist(id = "c1", title = "Survey"))
        checklistDao.upsertItem(item(id = "i1", checklistId = "c1"))
        checklistDao.upsertItem(item(id = "i2", checklistId = "c1"))

        checklistDao.softDeleteChecklistWithOutbox(
            id = "c1",
            updatedAt = 500,
            operation = outbox(
                operationId = "op-del",
                entityId = "c1",
                operationType = OutboxOperationType.DELETE,
            ),
        )

        assertTrue(checklistDao.observeItems("c1").first().isEmpty())
        assertTrue(checklistDao.getChecklist("c1")!!.isDeleted)
        assertEquals(SyncStatus.PENDING, checklistDao.getItem("i1")!!.syncStatus)
    }

    @Test
    fun `test deleteChecklist when called should cascade to its items`() = runTest {
        checklistDao.upsertChecklist(checklist(id = "c1", title = "Survey"))
        checklistDao.upsertItem(item(id = "i1", checklistId = "c1"))

        checklistDao.deleteChecklist("c1")

        assertNull(checklistDao.getItem("i1"))
    }

    @Test
    fun `test checklistByServerId when the row is synced should find it`() = runTest {
        checklistDao.upsertChecklist(
            checklist(id = "local-1", title = "Survey").copy(
                serverId = "remote-1",
                syncStatus = SyncStatus.SYNCED,
            ),
        )

        assertEquals("local-1", checklistDao.getChecklistByServerId("remote-1")?.id)
        assertNull(checklistDao.getChecklistByServerId("unknown"))
    }
}

// --- builders, so each test states only what it cares about ---

internal fun checklist(
    id: String,
    title: String = "Checklist",
    updatedAt: Long = 0,
) = ChecklistEntity(
    id = id,
    title = title,
    updatedAt = updatedAt,
    syncStatus = SyncStatus.PENDING,
)

internal fun item(
    id: String,
    checklistId: String,
    text: String = "Item",
    updatedAt: Long = 0,
) = ChecklistItemEntity(
    id = id,
    checklistId = checklistId,
    text = text,
    updatedAt = updatedAt,
    syncStatus = SyncStatus.PENDING,
)

internal fun outbox(
    operationId: String,
    entityId: String,
    entityType: OutboxEntityType = OutboxEntityType.CHECKLIST,
    operationType: OutboxOperationType = OutboxOperationType.CREATE,
    createdAt: Long = 0,
) = OutboxEntity(
    operationId = operationId,
    entityType = entityType,
    entityId = entityId,
    operationType = operationType,
    createdAt = createdAt,
)
