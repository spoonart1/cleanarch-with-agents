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

    // --- Item parent identity on the wire ------------------------------------
    // An item names its parent by the id the SERVER knows, never the local one.
    // Sending the local id made the pull's getChecklistByServerId lookup miss,
    // so every echoed item was dropped and the list read "0 of 0".

    @Test
    fun `test sync when an item is pushed should send the parent server id on the wire`() = runTest {
        // Given a parent checklist whose local id differs from its server id
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

        // Then the wire model carries the parent's server id, not "c1"
        assertEquals(
            "the wire checklistId must be the parent's server id, or the pull cannot match it",
            "srv-c1",
            network.pushed.single().item?.checklistId,
        )
    }

    @Test
    fun `test sync when the server echoes a pushed item should keep it attached to its parent`() = runTest {
        // Given a synced parent and a done item on it, queued for push
        checklistDao.upsertChecklist(
            checklistEntity(id = "c1", serverId = "srv-c1", syncStatus = SyncStatus.SYNCED),
        )
        checklistDao.upsertItemWithOutbox(
            item = itemEntity(
                id = "i1",
                checklistId = "c1",
                text = "Check the gauge",
                isDone = true,
                updatedAt = 1_000L,
            ),
            operation = outboxEntity(
                operationId = "op-i1",
                entityId = "i1",
                entityType = OutboxEntityType.CHECKLIST_ITEM,
            ),
        )

        // When the item is pushed
        engine.sync()

        // And the server echoes it back on a later pull, as it would
        val assignedServerId = checklistDao.getItem("i1")!!.serverId!!
        network.pullResponse = SyncResponse(
            items = listOf(
                NetworkChecklistItem(
                    id = assignedServerId,
                    checklistId = "srv-c1",
                    text = "Check the gauge",
                    isDone = true,
                    updatedAt = 9_999L,
                ),
            ),
            syncToken = "token-2",
        )
        engine.sync()

        // Then the item is still on its parent and still done: the count reads
        // 1 of 1 rather than 0 of 0.
        val stored = checklistDao.getItemByServerId(assignedServerId)
        assertEquals(
            "the echoed item must stay attached to its local parent",
            "c1",
            stored?.checklistId,
        )
        assertEquals("the done state must survive the round trip", true, stored?.isDone)
    }

    // --- Deferred items ------------------------------------------------------
    // An item cannot be pushed before its parent has a server id, because the
    // request has to name the parent the way the server knows it.

    @Test
    fun `test sync when an item parent has no server id should defer the push`() = runTest {
        // Given a parent that has never been pushed, so it has no server id
        checklistDao.upsertChecklist(checklistEntity(id = "c1"))

        // And an item on it queued for push
        checklistDao.upsertItemWithOutbox(
            item = itemEntity(id = "i1", checklistId = "c1"),
            operation = outboxEntity(
                operationId = "op-i1",
                entityId = "i1",
                entityType = OutboxEntityType.CHECKLIST_ITEM,
            ),
        )

        // When
        engine.sync()

        // Then nothing was sent, and the entry waits rather than being discarded
        assertTrue("an item with no parent server id must not be sent", network.pushed.isEmpty())
        assertEquals(
            "the deferred entry must stay queued",
            listOf("op-i1"),
            outboxDao.pendingOperations().map { it.operationId },
        )
    }

    @Test
    fun `test sync when the parent gains a server id should push the deferred item on a later pass`() = runTest {
        // Given a deferred item whose parent has not been pushed
        checklistDao.upsertChecklist(checklistEntity(id = "c1"))
        checklistDao.upsertItemWithOutbox(
            item = itemEntity(id = "i1", checklistId = "c1"),
            operation = outboxEntity(
                operationId = "op-i1",
                entityId = "i1",
                entityType = OutboxEntityType.CHECKLIST_ITEM,
            ),
        )
        engine.sync()

        // When the parent is queued for push. Only the outbox entry is added:
        // re-upserting the checklist row would REPLACE it, and the cascade on
        // checklist_items would take the item with it.
        outboxDao.insert(outboxEntity(operationId = "op-c1", entityId = "c1", createdAt = 1))
        engine.sync()

        // And one more sync runs. The item is queued ahead of its parent, so it
        // is examined before the parent's push in that pass and only becomes
        // sendable on the one after — deferred, never dropped.
        engine.sync()

        // Then the item follows, and the outbox drains
        val parentServerId = checklistDao.getChecklist("c1")!!.serverId
        assertEquals(
            "the item must be sent once the parent is known to the server",
            parentServerId,
            network.pushed.mapNotNull { it.item }.single().checklistId,
        )
        assertTrue("the outbox must drain", outboxDao.pendingOperations().isEmpty())

        // And the ordinary deferrals it took to get there did not spend the
        // whole attempt budget: bounding deferrals must not break this path.
        assertEquals(
            "a normally deferred item must still reach the server, not be abandoned",
            SyncStatus.SYNCED,
            checklistDao.getItem("i1")!!.syncStatus,
        )
    }

    @Test
    fun `test sync when an item is deferred once should count the attempt and keep it queued`() = runTest {
        // Given a parent that has never been pushed, so it has no server id
        checklistDao.upsertChecklist(checklistEntity(id = "c1"))

        // And an item on it queued for push
        checklistDao.upsertItemWithOutbox(
            item = itemEntity(id = "i1", checklistId = "c1"),
            operation = outboxEntity(
                operationId = "op-i1",
                entityId = "i1",
                entityType = OutboxEntityType.CHECKLIST_ITEM,
            ),
        )

        // When
        engine.sync()

        // Then the deferral is counted against the entry's budget
        val queued = outboxDao.pendingOperations().single()
        assertEquals(
            "a deferral must spend an attempt, or a stuck entry would never surface",
            1,
            queued.attemptCount,
        )

        // And the entry is still queued: deferring is not failing
        assertEquals("op-i1", queued.operationId)
        assertEquals(
            "one deferral must not abandon the item",
            SyncStatus.PENDING,
            checklistDao.getItem("i1")!!.syncStatus,
        )
    }

    @Test
    fun `test sync when an item defers past the attempt limit should abandon it and mark it failed`() = runTest {
        // Given a parent that will never gain a server id, as happens when the
        // in-memory backend restarts and forgets a checklist the device synced
        checklistDao.upsertChecklist(checklistEntity(id = "c1"))

        // And an item on it that has already deferred up to the limit minus one
        checklistDao.upsertItemWithOutbox(
            item = itemEntity(id = "i1", checklistId = "c1"),
            operation = outboxEntity(
                operationId = "op-i1",
                entityId = "i1",
                entityType = OutboxEntityType.CHECKLIST_ITEM,
                attemptCount = SyncEngine.MAX_PUSH_ATTEMPTS - 1,
            ),
        )

        // When the next pass defers it once more
        engine.sync()

        // Then the entry is abandoned rather than deferring forever, invisibly
        assertTrue(
            "an endlessly deferred entry must not sit in the outbox unnoticed",
            outboxDao.pendingOperations().isEmpty(),
        )

        // And the item surfaces as failed, so the user can see it
        assertEquals(
            "the abandoned item must be visible as FAILED",
            SyncStatus.FAILED,
            checklistDao.getItem("i1")!!.syncStatus,
        )
    }

    @Test
    fun `test sync when one item is deferred should still push the rest of the queue`() = runTest {
        // Given an item whose parent has no server id, queued first
        checklistDao.upsertChecklist(checklistEntity(id = "c1"))
        checklistDao.upsertItemWithOutbox(
            item = itemEntity(id = "i1", checklistId = "c1"),
            operation = outboxEntity(
                operationId = "op-deferred",
                entityId = "i1",
                entityType = OutboxEntityType.CHECKLIST_ITEM,
                createdAt = 100,
            ),
        )

        // And an unrelated checklist queued behind it
        checklistDao.upsertChecklistWithOutbox(
            checklist = checklistEntity(id = "c2", title = "Inspection"),
            operation = outboxEntity(operationId = "op-other", entityId = "c2", createdAt = 200),
        )

        // When
        engine.sync()

        // Then the deferred entry does not block the one behind it
        assertEquals(
            "a deferred item must not stall the queue",
            listOf("op-other"),
            network.pushed.map { it.operationId },
        )
        assertEquals(
            "only the deferred entry stays queued",
            listOf("op-deferred"),
            outboxDao.pendingOperations().map { it.operationId },
        )
    }
}
