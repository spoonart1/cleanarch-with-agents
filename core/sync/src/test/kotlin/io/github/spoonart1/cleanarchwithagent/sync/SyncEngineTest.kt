package io.github.spoonart1.cleanarchwithagent.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.spoonart1.cleanarchwithagent.database.CleanArchDatabase
import io.github.spoonart1.cleanarchwithagent.database.dao.ChecklistDao
import io.github.spoonart1.cleanarchwithagent.database.dao.OutboxDao
import io.github.spoonart1.cleanarchwithagent.model.OutboxEntityType
import io.github.spoonart1.cleanarchwithagent.model.SyncStatus
import io.github.spoonart1.cleanarchwithagent.network.model.NetworkChecklist
import io.github.spoonart1.cleanarchwithagent.network.model.NetworkChecklistItem
import io.github.spoonart1.cleanarchwithagent.network.model.SyncResponse
import java.io.IOException
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
class SyncEngineTest {

    private lateinit var database: CleanArchDatabase
    private lateinit var checklistDao: ChecklistDao
    private lateinit var outboxDao: OutboxDao
    private lateinit var network: FakeNetwork
    private lateinit var tokenStore: FakeSyncTokenStore
    private lateinit var engine: SyncEngine

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CleanArchDatabase::class.java,
        ).build()
        checklistDao = database.checklistDao()
        outboxDao = database.outboxDao()
        network = FakeNetwork()
        tokenStore = FakeSyncTokenStore()
        engine = SyncEngine(
            checklistDao = checklistDao,
            outboxDao = outboxDao,
            network = network,
            tokenStore = tokenStore,
            monitor = SyncStatusMonitor(outboxDao),
        )
    }

    @After
    fun tearDown() = database.close()

    // --- 1. Sync success ---

    @Test
    fun `test sync when a change is pending should push it, mark it synced and clear the outbox`() = runTest {
        checklistDao.upsertChecklistWithOutbox(
            checklist = checklistEntity(id = "c1", title = "Survey"),
            operation = outboxEntity(operationId = "op-1", entityId = "c1"),
        )

        val result = engine.sync()

        assertEquals(SyncResult.Success, result)
        assertEquals(1, network.pushed.size)
        assertTrue("outbox should be drained", outboxDao.pendingOperations().isEmpty())

        val stored = checklistDao.getChecklist("c1")!!
        assertEquals(SyncStatus.SYNCED, stored.syncStatus)
        assertNotNull("server id should be recorded", stored.serverId)
    }

    @Test
    fun `test sync when several operations are queued should push them oldest first`() = runTest {
        checklistDao.upsertChecklistWithOutbox(
            checklistEntity(id = "c1"),
            outboxEntity(operationId = "op-old", entityId = "c1", createdAt = 100),
        )
        checklistDao.upsertChecklistWithOutbox(
            checklistEntity(id = "c2"),
            outboxEntity(operationId = "op-new", entityId = "c2", createdAt = 200),
        )

        engine.sync()

        assertEquals(
            listOf("op-old", "op-new"),
            network.pushed.map { it.operationId },
        )
    }

    // --- 2. Retry after failure ---

    @Test
    fun `test sync when a push fails should leave the change queued and report a retry`() = runTest {
        checklistDao.upsertChecklistWithOutbox(
            checklistEntity(id = "c1"),
            outboxEntity(operationId = "op-1", entityId = "c1"),
        )
        network.failWith = IOException("offline")

        val result = engine.sync()

        assertTrue(result is SyncResult.Retry)
        assertEquals(
            "the change must stay queued so it is not lost",
            1,
            outboxDao.pendingOperations().size,
        )
        assertEquals(1, outboxDao.pendingOperations().first().attemptCount)
    }

    @Test
    fun `test sync when a failure is transient should succeed on the next pass`() = runTest {
        checklistDao.upsertChecklistWithOutbox(
            checklistEntity(id = "c1"),
            outboxEntity(operationId = "op-1", entityId = "c1"),
        )
        network.failFirstPushes = 1

        val first = engine.sync()
        val second = engine.sync()

        assertTrue(first is SyncResult.Retry)
        assertEquals(SyncResult.Success, second)
        assertTrue(outboxDao.pendingOperations().isEmpty())
        assertEquals(SyncStatus.SYNCED, checklistDao.getChecklist("c1")!!.syncStatus)
    }

    @Test
    fun `test sync when the attempt limit is reached should abandon the change so the queue drains`() = runTest {
        checklistDao.upsertChecklistWithOutbox(
            checklistEntity(id = "c1"),
            // Already at the limit minus one, so the next failure gives up.
            outboxEntity(
                operationId = "op-1",
                entityId = "c1",
                attemptCount = SyncEngine.MAX_PUSH_ATTEMPTS - 1,
            ),
        )
        network.failWith = IOException("permanently rejected")

        engine.sync()

        assertTrue(
            "the poisoned entry must be dropped from the outbox",
            outboxDao.pendingOperations().isEmpty(),
        )
        assertEquals(
            "the record stays on the device, marked failed",
            SyncStatus.FAILED,
            checklistDao.getChecklist("c1")!!.syncStatus,
        )
    }

    // --- 3. Idempotent replay ---

    @Test
    fun `test sync when the same operation is replayed should not apply it twice`() = runTest {
        checklistDao.upsertChecklistWithOutbox(
            checklistEntity(id = "c1"),
            outboxEntity(operationId = "op-1", entityId = "c1"),
        )

        engine.sync()

        // Simulate the app dying after the server applied the change but before
        // the outbox entry was cleared: requeue the identical operation.
        outboxDao.insert(outboxEntity(operationId = "op-1", entityId = "c1"))
        engine.sync()

        assertEquals("the server saw the operation twice", 2, network.pushed.size)
        assertEquals("but applied it only once", 1, network.appliedCount)
    }

    // --- 4. A pending local change is never overwritten ---

    @Test
    fun `test sync when a local edit is still pending should not overwrite it with the remote change`() = runTest {
        // A checklist that has been synced before, then edited locally again.
        checklistDao.upsertChecklistWithOutbox(
            checklist = checklistEntity(
                id = "c1",
                title = "My local edit",
                serverId = "srv-1",
                updatedAt = 1_000L,
            ),
            operation = outboxEntity(operationId = "op-1", entityId = "c1"),
        )
        // The server has a newer version of the same record.
        network.pullResponse = SyncResponse(
            checklists = listOf(
                NetworkChecklist(id = "srv-1", title = "Someone else's edit", updatedAt = 9_999L),
            ),
            syncToken = "token-2",
        )
        // Pushing fails, so the local change is still pending when the pull runs.
        network.failFirstPushes = 1

        engine.sync()

        val stored = checklistDao.getChecklist("c1")!!
        assertEquals(
            "the local edit must survive even though the remote is newer",
            "My local edit",
            stored.title,
        )
        assertEquals(SyncStatus.CONFLICT, stored.syncStatus)
    }

    @Test
    fun `test sync when there is no pending local edit should apply the remote change`() = runTest {
        checklistDao.upsertChecklist(
            checklistEntity(
                id = "c1",
                title = "Old title",
                serverId = "srv-1",
                updatedAt = 1_000L,
                syncStatus = SyncStatus.SYNCED,
            ),
        )
        network.pullResponse = SyncResponse(
            checklists = listOf(
                NetworkChecklist(id = "srv-1", title = "Updated remotely", updatedAt = 9_999L),
            ),
            syncToken = "token-2",
        )

        engine.sync()

        val stored = checklistDao.getChecklist("c1")!!
        assertEquals("Updated remotely", stored.title)
        assertEquals(SyncStatus.SYNCED, stored.syncStatus)
    }

    @Test
    fun `test sync when the remote version is older should not clobber the newer local one`() = runTest {
        checklistDao.upsertChecklist(
            checklistEntity(
                id = "c1",
                title = "Newer local",
                serverId = "srv-1",
                updatedAt = 9_999L,
                syncStatus = SyncStatus.SYNCED,
            ),
        )
        network.pullResponse = SyncResponse(
            checklists = listOf(
                NetworkChecklist(id = "srv-1", title = "Stale remote", updatedAt = 1_000L),
            ),
            syncToken = "token-2",
        )

        engine.sync()

        assertEquals("Newer local", checklistDao.getChecklist("c1")!!.title)
    }

    @Test
    fun `test sync when a remote record is unseen should insert it locally`() = runTest {
        network.pullResponse = SyncResponse(
            checklists = listOf(
                NetworkChecklist(id = "srv-new", title = "From another device", updatedAt = 500L),
            ),
            syncToken = "token-2",
        )

        engine.sync()

        val stored = checklistDao.getChecklistByServerId("srv-new")
        assertEquals("From another device", stored?.title)
        assertEquals(SyncStatus.SYNCED, stored?.syncStatus)
    }

    // --- Sync token handling ---

    @Test
    fun `test sync when a pull succeeds should advance the sync token`() = runTest {
        network.pullResponse = SyncResponse(syncToken = "token-99")

        engine.sync()

        assertEquals("token-99", tokenStore.read())
    }

    @Test
    fun `test sync when a pull fails should leave the previous token in place`() = runTest {
        val store = FakeSyncTokenStore(token = "token-original")
        val failingEngine = SyncEngine(
            checklistDao = checklistDao,
            outboxDao = outboxDao,
            network = network,
            tokenStore = store,
            monitor = SyncStatusMonitor(outboxDao),
        )
        network.failWith = IOException("offline")

        failingEngine.sync()

        assertEquals(
            "advancing the token past unapplied changes would lose them",
            "token-original",
            store.read(),
        )
    }

    // --- Item paths ----------------------------------------------------------
    // The same four rules as above, exercised through checklist ITEMS. These
    // matter separately because an item carries a foreign key to its parent,
    // which gives the insert path a failure mode a checklist does not have.

    @Test
    fun `test sync when an item is pending should push it and mark it synced`() = runTest {
        // Given a synced parent checklist
        checklistDao.upsertChecklist(
            checklistEntity(id = "c1", serverId = "srv-c1", syncStatus = SyncStatus.SYNCED),
        )

        // And an item on it queued for push
        checklistDao.upsertItemWithOutbox(
            item = itemEntity(id = "i1", checklistId = "c1", text = "Check the gauge"),
            operation = outboxEntity(
                operationId = "op-i1",
                entityId = "i1",
                entityType = OutboxEntityType.CHECKLIST_ITEM,
            ),
        )

        // When
        engine.sync()

        // Then
        val stored = checklistDao.getItem("i1")!!
        assertEquals(SyncStatus.SYNCED, stored.syncStatus)
        assertNotNull("the server id must be adopted after a push", stored.serverId)
        assertTrue(outboxDao.pendingOperations().isEmpty())
    }

    @Test
    fun `test sync when a remote item is unseen should insert it under its parent`() = runTest {
        // Given a parent checklist that exists locally
        checklistDao.upsertChecklist(
            checklistEntity(id = "c1", serverId = "srv-c1", syncStatus = SyncStatus.SYNCED),
        )

        // And the server reports an item the device has not seen
        network.pullResponse = SyncResponse(
            items = listOf(
                NetworkChecklistItem(
                    id = "srv-i1",
                    checklistId = "srv-c1",
                    text = "From another device",
                    updatedAt = 500L,
                ),
            ),
            syncToken = "token-2",
        )

        // When
        engine.sync()

        // Then
        val stored = checklistDao.getItemByServerId("srv-i1")
        assertEquals("From another device", stored?.text)
        assertEquals("c1", stored?.checklistId)
        assertEquals(SyncStatus.SYNCED, stored?.syncStatus)
    }

    @Test
    fun `test sync when a remote item has no local parent should skip it`() = runTest {
        // Given no checklist with this server id exists locally

        // And the server reports an item belonging to it
        network.pullResponse = SyncResponse(
            items = listOf(
                NetworkChecklistItem(
                    id = "srv-orphan",
                    checklistId = "srv-unknown",
                    text = "Orphan",
                    updatedAt = 500L,
                ),
            ),
            syncToken = "token-2",
        )

        // When
        engine.sync()

        // Then the row is skipped rather than violating the foreign key.
        assertNull(
            "an item whose parent is absent must not be inserted",
            checklistDao.getItemByServerId("srv-orphan"),
        )
    }

    @Test
    fun `test sync when a remote item is newer should overwrite the local copy`() = runTest {
        // Given a synced parent and a synced item
        checklistDao.upsertChecklist(
            checklistEntity(id = "c1", serverId = "srv-c1", syncStatus = SyncStatus.SYNCED),
        )
        checklistDao.upsertItem(
            itemEntity(
                id = "i1",
                checklistId = "c1",
                serverId = "srv-i1",
                text = "Old text",
                updatedAt = 1_000L,
                syncStatus = SyncStatus.SYNCED,
            ),
        )

        // And the server has a newer version
        network.pullResponse = SyncResponse(
            items = listOf(
                NetworkChecklistItem(
                    id = "srv-i1",
                    checklistId = "srv-c1",
                    text = "New text",
                    isDone = true,
                    note = "Server note",
                    updatedAt = 9_999L,
                ),
            ),
            syncToken = "token-2",
        )

        // When
        engine.sync()

        // Then
        val stored = checklistDao.getItem("i1")!!
        assertEquals("New text", stored.text)
        assertTrue(stored.isDone)
        assertEquals("Server note", stored.note)
        assertEquals(SyncStatus.SYNCED, stored.syncStatus)
    }

    @Test
    fun `test sync when a remote item is older should not clobber the local copy`() = runTest {
        // Given a synced parent and a locally newer item
        checklistDao.upsertChecklist(
            checklistEntity(id = "c1", serverId = "srv-c1", syncStatus = SyncStatus.SYNCED),
        )
        checklistDao.upsertItem(
            itemEntity(
                id = "i1",
                checklistId = "c1",
                serverId = "srv-i1",
                text = "Newer local text",
                updatedAt = 9_999L,
                syncStatus = SyncStatus.SYNCED,
            ),
        )

        // And the server reports an older version
        network.pullResponse = SyncResponse(
            items = listOf(
                NetworkChecklistItem(
                    id = "srv-i1",
                    checklistId = "srv-c1",
                    text = "Stale server text",
                    updatedAt = 1_000L,
                ),
            ),
            syncToken = "token-2",
        )

        // When
        engine.sync()

        // Then
        assertEquals("Newer local text", checklistDao.getItem("i1")!!.text)
    }

    @Test
    fun `test sync when an item edit is still pending should flag a conflict`() = runTest {
        // Given a synced parent
        checklistDao.upsertChecklist(
            checklistEntity(id = "c1", serverId = "srv-c1", syncStatus = SyncStatus.SYNCED),
        )

        // And an item edited locally and not yet pushed
        checklistDao.upsertItemWithOutbox(
            item = itemEntity(
                id = "i1",
                checklistId = "c1",
                serverId = "srv-i1",
                text = "My local edit",
                updatedAt = 1_000L,
            ),
            operation = outboxEntity(
                operationId = "op-i1",
                entityId = "i1",
                entityType = OutboxEntityType.CHECKLIST_ITEM,
            ),
        )

        // And the server has a newer version of the same item
        network.pullResponse = SyncResponse(
            items = listOf(
                NetworkChecklistItem(
                    id = "srv-i1",
                    checklistId = "srv-c1",
                    text = "Someone else's edit",
                    updatedAt = 9_999L,
                ),
            ),
            syncToken = "token-2",
        )

        // And the push fails, so the local edit is still pending during the pull
        network.failFirstPushes = 1

        // When
        engine.sync()

        // Then
        val stored = checklistDao.getItem("i1")!!
        assertEquals(
            "the local edit must survive even though the remote is newer",
            "My local edit",
            stored.text,
        )
        assertEquals(SyncStatus.CONFLICT, stored.syncStatus)
    }
}
