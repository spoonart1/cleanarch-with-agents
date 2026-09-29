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
    fun `test createChecklist when called should store it pending and queue an outbox entry`() = runTest {
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
    fun `test createChecklist when the write succeeds should request a sync`() = runTest {
        repository.createChecklist("Site survey")

        assertEquals(1, scheduler.requestCount)
    }

    @Test
    fun `test createChecklist when a sync is requested should have already made the write durable`() = runTest {
        // The point of offline-first: the row exists whether or not the sync
        // ever runs. The recording scheduler never runs one.
        val id = repository.createChecklist("Site survey")

        assertEquals(1, repository.observeChecklists().first().size)
        assertEquals("Site survey", repository.observeChecklists().first().first().title)
        assertEquals(id, repository.observeChecklists().first().first().id)
    }

    @Test
    fun `test renameChecklist when called should mark it pending again and queue an update`() = runTest {
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
    fun `test deleteChecklist when called should hide the row but keep it until the server confirms`() = runTest {
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
    fun `test addItem when called should store it pending and queue an outbox entry`() = runTest {
        val checklistId = repository.createChecklist("Survey")

        val itemId = repository.addItem(checklistId, "Check the gauge")

        val stored = checklistDao.getItem(itemId)!!
        assertEquals("Check the gauge", stored.text)
        assertEquals(SyncStatus.PENDING, stored.syncStatus)
        assertEquals(2, outboxDao.pendingOperations().size)
    }

    @Test
    fun `test setItemDone when an item is ticked should mark it pending`() = runTest {
        val checklistId = repository.createChecklist("Survey")
        val itemId = repository.addItem(checklistId, "Check the gauge")
        checklistDao.setItemSyncStatus(itemId, SyncStatus.SYNCED)

        repository.setItemDone(itemId, isDone = true)

        val stored = checklistDao.getItem(itemId)!!
        assertTrue(stored.isDone)
        assertEquals(SyncStatus.PENDING, stored.syncStatus)
    }

    @Test
    fun `test setItemNote when a note is added should keep the other fields`() = runTest {
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
    fun `test observeItems when other checklists have items should return only the requested ones`() = runTest {
        val first = repository.createChecklist("First")
        val second = repository.createChecklist("Second")
        repository.addItem(first, "Item A")
        repository.addItem(second, "Item B")

        val items = repository.observeItems(first).first()

        assertEquals(listOf("Item A"), items.map { it.text })
    }

    @Test
    fun `test observeChecklistSummaries when a checklist has items should report its counts`() =
        runTest {
            // Given a checklist with three items
            val checklistId = repository.createChecklist("Survey")
            val first = repository.addItem(checklistId, "Item A")
            val second = repository.addItem(checklistId, "Item B")
            repository.addItem(checklistId, "Item C")

            // And two of them are done
            repository.setItemDone(first, true)
            repository.setItemDone(second, true)

            // When
            val summary = repository.observeChecklistSummaries().first().single()

            // Then
            assertEquals("Survey", summary.checklist.title)
            assertEquals(3, summary.itemCount)
            assertEquals(2, summary.doneCount)
        }

    @Test
    fun `test observeChecklistSummaries when a checklist has no items should report zero counts`() =
        runTest {
            // Given a checklist with no items
            repository.createChecklist("Empty")

            // When
            val summary = repository.observeChecklistSummaries().first().single()

            // Then
            assertEquals(0, summary.itemCount)
            assertEquals(0, summary.doneCount)
        }

    @Test
    fun `test observeItems when an item has every field set should map them all to the domain model`() =
        runTest {
            // Given a checklist with one item
            val checklistId = repository.createChecklist("Survey")
            val itemId = repository.addItem(checklistId, "Check the gauge")

            // And that item has been ticked and annotated
            repository.setItemDone(itemId, true)
            repository.setItemNote(itemId, "Read 4.2 bar")

            // When
            val item = repository.observeItems(checklistId).first().single()

            // Then every field survives the entity-to-domain mapping. Asserting
            // the whole model rather than one field is deliberate: a mapper bug
            // that drops a field is exactly what this guards against, and a test
            // that checks only `text` would not catch it.
            assertEquals(itemId, item.id)
            assertEquals(checklistId, item.checklistId)
            assertEquals("Check the gauge", item.text)
            assertTrue(item.isDone)
            assertEquals("Read 4.2 bar", item.note)
            assertEquals(SyncStatus.PENDING, item.syncStatus)
            assertNull("an unsynced item has no server id yet", item.serverId)
        }

    @Test
    fun `test renameChecklist when the record is missing should do nothing rather than throw`() = runTest {
        repository.renameChecklist("does-not-exist", "New name")
        repository.setItemDone("does-not-exist", isDone = true)

        assertTrue(outboxDao.pendingOperations().isEmpty())
        assertEquals(0, scheduler.requestCount)
    }

    @Test
    fun `test requestSync when called should ask for an immediate sync`() {
        repository.requestSync()

        assertEquals(1, scheduler.syncNowCount)
    }

    @Test
    fun `test createChecklist when called repeatedly should give each operation a distinct id`() = runTest {
        val id = repository.createChecklist("Survey")
        repository.renameChecklist(id, "Renamed")

        val operationIds = outboxDao.pendingOperations().map { it.operationId }

        assertEquals(operationIds.size, operationIds.toSet().size)
    }
}
