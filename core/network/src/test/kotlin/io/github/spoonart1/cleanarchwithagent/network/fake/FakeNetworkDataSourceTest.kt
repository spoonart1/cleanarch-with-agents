package io.github.spoonart1.cleanarchwithagent.network.fake

import io.github.spoonart1.cleanarchwithagent.common.Clock
import io.github.spoonart1.cleanarchwithagent.network.model.NetworkChecklist
import io.github.spoonart1.cleanarchwithagent.network.model.PushRequest
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A controllable clock, so assertions about updatedAt are exact. */
private class FakeClock(var now: Long = 1_000L) : Clock {
    override fun nowMillis(): Long = now
}

class FakeNetworkDataSourceTest {

    private val simulator = NetworkSimulator()
    private val clock = FakeClock()
    private val dataSource = FakeNetworkDataSource(simulator, clock)

    @Test
    fun `push assigns a server id on create`() = runTest {
        val response = dataSource.push(createChecklist("op-1", "local-1", "Survey"))

        assertEquals("op-1", response.operationId)
        assertTrue(response.serverId.startsWith("srv-checklist-"))
        assertEquals(1_000L, response.updatedAt)
    }

    @Test
    fun `replaying an operation returns the original result and does not duplicate`() = runTest {
        val request = createChecklist("op-1", "local-1", "Survey")

        val first = dataSource.push(request)
        // The client could not tell whether the first response was lost, so it
        // retries the identical operation.
        clock.now = 9_999L
        val replay = dataSource.push(request)

        assertEquals(
            "a replayed operation must return the original response",
            first,
            replay,
        )
        // And must not have created a second checklist server-side.
        assertEquals(1, dataSource.pull(syncToken = null).checklists.size)
    }

    @Test
    fun `distinct operations on the same entity both apply`() = runTest {
        dataSource.push(createChecklist("op-1", "local-1", "Survey"))
        dataSource.push(createChecklist("op-2", "local-2", "Inspection"))

        assertEquals(2, dataSource.pull(syncToken = null).checklists.size)
    }

    @Test
    fun `pull returns only records newer than the sync token`() = runTest {
        dataSource.seedChecklist(
            NetworkChecklist(id = "old", title = "Old", updatedAt = 100),
        )
        dataSource.seedChecklist(
            NetworkChecklist(id = "new", title = "New", updatedAt = 300),
        )

        val response = dataSource.pull(syncToken = "200")

        assertEquals(listOf("New"), response.checklists.map { it.title })
    }

    @Test
    fun `pull with a null token returns everything`() = runTest {
        dataSource.seedChecklist(
            NetworkChecklist(id = "a", title = "A", updatedAt = 100),
        )
        dataSource.seedChecklist(
            NetworkChecklist(id = "b", title = "B", updatedAt = 300),
        )

        assertEquals(2, dataSource.pull(syncToken = null).checklists.size)
    }

    @Test
    fun `pull returns a sync token for the next call`() = runTest {
        clock.now = 5_000L

        assertEquals("5000", dataSource.pull(syncToken = null).syncToken)
    }

    @Test
    fun `offline mode fails both push and pull`() = runTest {
        simulator.setMode(NetworkMode.OFFLINE)

        val pull = runCatching { dataSource.pull(syncToken = null) }
        val push = runCatching { dataSource.push(createChecklist("op-1", "l1", "Survey")) }

        assertTrue(pull.exceptionOrNull() is IOException)
        assertTrue(push.exceptionOrNull() is IOException)
    }

    @Test
    fun `a push that failed offline is not recorded as applied`() = runTest {
        val request = createChecklist("op-1", "local-1", "Survey")
        simulator.setMode(NetworkMode.OFFLINE)
        runCatching { dataSource.push(request) }

        // Coming back online, the same operation must still apply — the failed
        // attempt must not have been mistaken for a completed one.
        simulator.setMode(NetworkMode.NORMAL)
        val response = dataSource.push(request)

        assertEquals("op-1", response.operationId)
        assertEquals(1, dataSource.pull(syncToken = null).checklists.size)
    }

    @Test
    fun `clear resets server state`() = runTest {
        dataSource.push(createChecklist("op-1", "local-1", "Survey"))

        dataSource.clear()

        assertTrue(dataSource.pull(syncToken = null).checklists.isEmpty())
    }

    private fun createChecklist(
        operationId: String,
        localId: String,
        title: String,
    ) = PushRequest(
        operationId = operationId,
        operation = FakeNetworkDataSource.OPERATION_CREATE,
        checklist = NetworkChecklist(id = localId, title = title, updatedAt = 0),
    )
}
