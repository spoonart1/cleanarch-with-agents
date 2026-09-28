package io.github.spoonart1.cleanarchwithagent.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.spoonart1.cleanarchwithagent.database.CleanArchDatabase
import io.github.spoonart1.cleanarchwithagent.database.dao.ChecklistDao
import io.github.spoonart1.cleanarchwithagent.database.dao.OutboxDao
import io.github.spoonart1.cleanarchwithagent.model.SyncStatus
import io.github.spoonart1.cleanarchwithagent.network.model.NetworkChecklist
import io.github.spoonart1.cleanarchwithagent.network.model.SyncResponse
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun `a pending change is pushed, marked synced, and cleared from the outbox`() = runTest {
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
    fun `operations are pushed oldest first`() = runTest {
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
    fun `a failed push leaves the change queued and reports retry`() = runTest {
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
    fun `a transient failure succeeds on the next pass`() = runTest {
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
    fun `a change is abandoned after the attempt limit so it cannot block the queue`() = runTest {
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
    fun `replaying the same operation does not apply it twice`() = runTest {
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
    fun `a remote change does not overwrite a local edit that is still pending`() = runTest {
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
    fun `a remote change is applied when there is no pending local edit`() = runTest {
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
    fun `an older remote version does not clobber a newer local one`() = runTest {
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
    fun `an unseen remote record is inserted locally`() = runTest {
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
    fun `the sync token advances only after a successful pull`() = runTest {
        network.pullResponse = SyncResponse(syncToken = "token-99")

        engine.sync()

        assertEquals("token-99", tokenStore.read())
    }

    @Test
    fun `a failed pull leaves the previous token in place`() = runTest {
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
}
