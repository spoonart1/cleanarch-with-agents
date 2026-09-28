package io.github.spoonart1.cleanarchwithagent.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.spoonart1.cleanarchwithagent.common.Clock
import io.github.spoonart1.cleanarchwithagent.common.IdGenerator
import io.github.spoonart1.cleanarchwithagent.database.CleanArchDatabase
import io.github.spoonart1.cleanarchwithagent.database.dao.ChecklistDao
import io.github.spoonart1.cleanarchwithagent.database.dao.OutboxDao
import io.github.spoonart1.cleanarchwithagent.model.OutboxOperationType
import io.github.spoonart1.cleanarchwithagent.model.SyncStatus
import io.github.spoonart1.cleanarchwithagent.sync.SyncScheduler
import io.github.spoonart1.cleanarchwithagent.sync.SyncStatusMonitor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Records what was asked for, so the tests need no WorkManager. */
private class RecordingSyncScheduler : SyncScheduler {
    var requestCount = 0
    var syncNowCount = 0
    var periodicCount = 0

    override fun requestSync() { requestCount++ }
    override fun syncNow() { syncNowCount++ }
    override fun schedulePeriodicSync() { periodicCount++ }
}

private class FixedClock(var now: Long = 1_000L) : Clock {
    override fun nowMillis(): Long = now
}

private class SequentialIdGenerator : IdGenerator {
    private var counter = 0
    override fun newId(): String = "id-${++counter}"
}

@RunWith(RobolectricTestRunner::class)
class OfflineFirstChecklistRepositoryTest {

    private lateinit var database: CleanArchDatabase
    private lateinit var checklistDao: ChecklistDao
    private lateinit var outboxDao: OutboxDao
    private lateinit var scheduler: RecordingSyncScheduler
    private lateinit var clock: FixedClock
    private lateinit var repository: OfflineFirstChecklistRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CleanArchDatabase::class.java,
        ).build()
        checklistDao = database.checklistDao()
        outboxDao = database.outboxDao()
        scheduler = RecordingSyncScheduler()
        clock = FixedClock()
        repository = OfflineFirstChecklistRepository(
            checklistDao = checklistDao,
            syncScheduler = scheduler,
            syncStatusMonitor = SyncStatusMonitor(outboxDao),
            clock = clock,
            idGenerator = SequentialIdGenerator(),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `creating a checklist stores it as pending and queues an outbox entry`() = runTest {
        val id = repository.createChecklist("Site survey")

        val stored = checklistDao.getChecklist(id)!!
        assertEquals("Site survey", stored.title)
        assertEquals(SyncStatus.PENDING, stored.syncStatus)
        assertNull("a new checklist has no server id yet", stored.serverId)

        val queued = outboxDao.pendingOperations()
        assertEquals(1, queued.size)
        assertEquals(id, queued.first().entityId)
        assertEquals(OutboxOperationType.CREATE, queued.first().operationType)
    }

    @Test
    fun `a write requests a sync`() = runTest {
        repository.createChecklist("Site survey")

        assertEquals(1, scheduler.requestCount)
    }

    @Test
    fun `the write is durable before the sync is requested`() = runTest {
        // The point of offline-first: the row exists whether or not the sync
        // ever runs. The recording scheduler never runs one.
        val id = repository.createChecklist("Site survey")

        assertEquals(1, repository.observeChecklists().first().size)
        assertEquals("Site survey", repository.observeChecklists().first().first().title)
        assertEquals(id, repository.observeChecklists().first().first().id)
    }

    @Test
    fun `renaming marks the checklist pending again and queues an update`() = runTest {
        val id = repository.createChecklist("Original")
        outboxDao.deleteByOperationId(outboxDao.pendingOperations().first().operationId)
        checklistDao.setChecklistSyncStatus(id, SyncStatus.SYNCED)

        clock.now = 2_000L
        repository.renameChecklist(id, "Renamed")

        val stored = checklistDao.getChecklist(id)!!
        assertEquals("Renamed", stored.title)
        assertEquals(SyncStatus.PENDING, stored.syncStatus)
        assertEquals(2_000L, stored.updatedAt)
        assertEquals(OutboxOperationType.UPDATE, outboxDao.pendingOperations().first().operationType)
    }

    @Test
    fun `deleting a checklist hides it but keeps the row until the server confirms`() = runTest {
        val id = repository.createChecklist("Doomed")

        repository.deleteChecklist(id)

        assertTrue(
            "a deleted checklist disappears from the UI",
            repository.observeChecklists().first().isEmpty(),
        )
        assertTrue(
            "but the row survives so the deletion can still be pushed",
            checklistDao.getChecklist(id)!!.isDeleted,
        )
        assertTrue(
            outboxDao.pendingOperations().any {
                it.operationType == OutboxOperationType.DELETE
            },
        )
    }

    @Test
    fun `adding an item stores it pending and queues an entry`() = runTest {
        val checklistId = repository.createChecklist("Survey")

        val itemId = repository.addItem(checklistId, "Check the gauge")

        val stored = checklistDao.getItem(itemId)!!
        assertEquals("Check the gauge", stored.text)
        assertEquals(SyncStatus.PENDING, stored.syncStatus)
        assertEquals(2, outboxDao.pendingOperations().size)
    }

    @Test
    fun `ticking an item marks it pending`() = runTest {
        val checklistId = repository.createChecklist("Survey")
        val itemId = repository.addItem(checklistId, "Check the gauge")
        checklistDao.setItemSyncStatus(itemId, SyncStatus.SYNCED)

        repository.setItemDone(itemId, isDone = true)

        val stored = checklistDao.getItem(itemId)!!
        assertTrue(stored.isDone)
        assertEquals(SyncStatus.PENDING, stored.syncStatus)
    }

    @Test
    fun `adding a note keeps the item's other fields`() = runTest {
        val checklistId = repository.createChecklist("Survey")
        val itemId = repository.addItem(checklistId, "Check the gauge")
        repository.setItemDone(itemId, isDone = true)

        repository.setItemNote(itemId, "Reading was 4.2 bar")

        val stored = checklistDao.getItem(itemId)!!
        assertEquals("Reading was 4.2 bar", stored.note)
        assertTrue("the done flag must survive a note edit", stored.isDone)
        assertEquals("Check the gauge", stored.text)
    }

    @Test
    fun `observeItems returns only the requested checklist's items`() = runTest {
        val first = repository.createChecklist("First")
        val second = repository.createChecklist("Second")
        repository.addItem(first, "Item A")
        repository.addItem(second, "Item B")

        val items = repository.observeItems(first).first()

        assertEquals(listOf("Item A"), items.map { it.text })
    }

    @Test
    fun `editing a missing record does nothing rather than throwing`() = runTest {
        repository.renameChecklist("does-not-exist", "New name")
        repository.setItemDone("does-not-exist", isDone = true)

        assertTrue(outboxDao.pendingOperations().isEmpty())
        assertEquals(0, scheduler.requestCount)
    }

    @Test
    fun `requestSync asks for an immediate sync`() {
        repository.requestSync()

        assertEquals(1, scheduler.syncNowCount)
    }

    @Test
    fun `each queued operation gets a distinct id so retries stay idempotent`() = runTest {
        val id = repository.createChecklist("Survey")
        repository.renameChecklist(id, "Renamed")

        val operationIds = outboxDao.pendingOperations().map { it.operationId }

        assertEquals(operationIds.size, operationIds.toSet().size)
    }
}
