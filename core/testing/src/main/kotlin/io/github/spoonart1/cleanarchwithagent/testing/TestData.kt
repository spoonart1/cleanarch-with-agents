package io.github.spoonart1.cleanarchwithagent.testing

import io.github.spoonart1.cleanarchwithagent.common.Clock
import io.github.spoonart1.cleanarchwithagent.common.IdGenerator
import io.github.spoonart1.cleanarchwithagent.model.Checklist
import io.github.spoonart1.cleanarchwithagent.model.ChecklistItem
import io.github.spoonart1.cleanarchwithagent.model.SyncStatus

/**
 * Builders with sensible defaults, so a test states only the field it is about.
 *
 * `testChecklist(title = "Survey")` reads better than a constructor call with
 * five irrelevant arguments, and adding a field later does not break every test.
 */
fun testChecklist(
    id: String = "checklist-1",
    title: String = "Site survey",
    serverId: String? = null,
    updatedAt: Long = 0L,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
) = Checklist(
    id = id,
    serverId = serverId,
    title = title,
    updatedAt = updatedAt,
    syncStatus = syncStatus,
)

fun testChecklistItem(
    id: String = "item-1",
    checklistId: String = "checklist-1",
    text: String = "Check the gauge",
    isDone: Boolean = false,
    note: String? = null,
    serverId: String? = null,
    updatedAt: Long = 0L,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
) = ChecklistItem(
    id = id,
    serverId = serverId,
    checklistId = checklistId,
    text = text,
    isDone = isDone,
    note = note,
    updatedAt = updatedAt,
    syncStatus = syncStatus,
)

/** A clock a test can move by hand, so timestamps are exact. */
class TestClock(var now: Long = 1_000L) : Clock {
    override fun nowMillis(): Long = now
}

/** Predictable ids: `id-1`, `id-2`, ... so assertions can name them. */
class TestIdGenerator(private val prefix: String = "id") : IdGenerator {
    private var counter = 0
    override fun newId(): String = "$prefix-${++counter}"
}
