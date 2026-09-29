package io.github.spoonart1.cleanarchwithagent.network.fake

import io.github.spoonart1.cleanarchwithagent.common.Clock
import io.github.spoonart1.cleanarchwithagent.network.model.NetworkChecklist
import io.github.spoonart1.cleanarchwithagent.network.model.NetworkChecklistItem
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
    fun `test push when the operation is a create should assign a server id`() = runTest {
        val response = dataSource.push(createChecklist("op-1", "local-1", "Survey"))

        assertEquals("op-1", response.operationId)
        assertTrue(response.serverId.startsWith("srv-checklist-"))
        assertEquals(1_000L, response.updatedAt)
    }

    @Test
    fun `test push when an operation is replayed should return the original result without duplicating`() = runTest {
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
    fun `test push when two distinct operations target one entity should apply both`() = runTest {
        dataSource.push(createChecklist("op-1", "local-1", "Survey"))
        dataSource.push(createChecklist("op-2", "local-2", "Inspection"))

        assertEquals(2, dataSource.pull(syncToken = null).checklists.size)
    }

    @Test
    fun `test pull when given a sync token should return only records newer than it`() = runTest {
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
    fun `test pull when the token is null should return everything`() = runTest {
        dataSource.seedChecklist(
            NetworkChecklist(id = "a", title = "A", updatedAt = 100),
        )
        dataSource.seedChecklist(
            NetworkChecklist(id = "b", title = "B", updatedAt = 300),
        )

        assertEquals(2, dataSource.pull(syncToken = null).checklists.size)
    }

    @Test
    fun `test pull when called should return a sync token for the next call`() = runTest {
        clock.now = 5_000L

        assertEquals("5000", dataSource.pull(syncToken = null).syncToken)
    }

    @Test
    fun `test push and pull when the simulator is offline should both fail`() = runTest {
        simulator.setMode(NetworkMode.OFFLINE)

        val pull = runCatching { dataSource.pull(syncToken = null) }
        val push = runCatching { dataSource.push(createChecklist("op-1", "l1", "Survey")) }

        assertTrue(pull.exceptionOrNull() is IOException)
        assertTrue(push.exceptionOrNull() is IOException)
    }

    @Test
    fun `test push when it failed offline should not be recorded as applied`() = runTest {
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
    fun `test clear when called should reset the server state`() = runTest {
        dataSource.push(createChecklist("op-1", "local-1", "Survey"))

        dataSource.clear()

        assertTrue(dataSource.pull(syncToken = null).checklists.isEmpty())
    }

    // --- Items ---------------------------------------------------------------
    // The item half of the fake backend, which the checklist tests above do not
    // touch at all.

    @Test
    fun `test push when the operation is an item create should assign a server id`() = runTest {
        // Given
        val request = createItem("op-1", "local-item-1", "Check the gauge")

        // When
        val response = dataSource.push(request)

        // Then
        assertEquals("op-1", response.operationId)
        assertTrue(response.serverId.startsWith("srv-item-"))
        assertEquals(1_000L, response.updatedAt)
    }

    @Test
    fun `test push when an item is updated should keep its server id`() = runTest {
        // Given an item already on the server
        val created = dataSource.push(createItem("op-1", "local-item-1", "Original"))

        // When it is pushed again as an update
        val updated = dataSource.push(
            PushRequest(
                operationId = "op-2",
                operation = FakeNetworkDataSource.OPERATION_UPDATE,
                item = NetworkChecklistItem(
                    id = created.serverId,
                    checklistId = "srv-checklist-1",
                    text = "Edited",
                    updatedAt = 0,
                ),
            ),
        )

        // Then
        assertEquals(created.serverId, updated.serverId)
        assertEquals("Edited", dataSource.pull(syncToken = null).items.single().text)
    }

    @Test
    fun `test push when an item is deleted should mark it deleted rather than dropping it`() =
        runTest {
            // Given an item already on the server
            val created = dataSource.push(createItem("op-1", "local-item-1", "Doomed"))

            // When a delete is pushed for it
            dataSource.push(
                PushRequest(
                    operationId = "op-2",
                    operation = FakeNetworkDataSource.OPERATION_DELETE,
                    item = NetworkChecklistItem(
                        id = created.serverId,
                        checklistId = "srv-checklist-1",
                        text = "Doomed",
                        updatedAt = 0,
                    ),
                ),
            )

            // Then the row is still sent, flagged deleted, so other devices can
            // apply the deletion rather than silently keeping the item.
            assertTrue(dataSource.pull(syncToken = null).items.single().isDeleted)
        }

    @Test
    fun `test seedItem when called should make the item visible to pull`() = runTest {
        // Given
        dataSource.seedItem(
            NetworkChecklistItem(
                id = "srv-item-seeded",
                checklistId = "srv-checklist-1",
                text = "Seeded",
                updatedAt = 500L,
            ),
        )

        // When
        val response = dataSource.pull(syncToken = null)

        // Then
        assertEquals("Seeded", response.items.single().text)
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

    private fun createItem(
        operationId: String,
        localId: String,
        text: String,
        checklistId: String = "srv-checklist-1",
    ) = PushRequest(
        operationId = operationId,
        operation = FakeNetworkDataSource.OPERATION_CREATE,
        item = NetworkChecklistItem(
            id = localId,
            checklistId = checklistId,
            text = text,
            updatedAt = 0,
        ),
    )
}
