package io.github.spoonart1.cleanarchwithagent.data.mapper

import io.github.spoonart1.cleanarchwithagent.database.entity.ChecklistEntity
import io.github.spoonart1.cleanarchwithagent.database.entity.ChecklistItemEntity
import io.github.spoonart1.cleanarchwithagent.model.Checklist
import io.github.spoonart1.cleanarchwithagent.model.ChecklistItem
import io.github.spoonart1.cleanarchwithagent.model.ChecklistSummary
import io.github.spoonart1.cleanarchwithagent.database.dao.ChecklistSummary as DatabaseChecklistSummary

/**
 * Database types in, domain types out.
 *
 * This is the boundary: `ChecklistEntity` must not appear above `core:data`.
 * Keeping the mapping in one file makes it obvious when something starts to
 * leak, and lets the storage shape change without touching the UI.
 */
internal fun ChecklistEntity.toDomain() = Checklist(
    id = id,
    serverId = serverId,
    title = title,
    updatedAt = updatedAt,
    syncStatus = syncStatus,
)

internal fun DatabaseChecklistSummary.toDomain() = ChecklistSummary(
    checklist = checklist.toDomain(),
    itemCount = itemCount,
    doneCount = doneCount,
)

internal fun ChecklistItemEntity.toDomain() = ChecklistItem(
    id = id,
    serverId = serverId,
    checklistId = checklistId,
    text = text,
    isDone = isDone,
    note = note,
    updatedAt = updatedAt,
    syncStatus = syncStatus,
)
