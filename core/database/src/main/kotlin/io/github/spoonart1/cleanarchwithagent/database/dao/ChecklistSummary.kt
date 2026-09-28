package io.github.spoonart1.cleanarchwithagent.database.dao

import androidx.room.ColumnInfo
import androidx.room.Embedded
import io.github.spoonart1.cleanarchwithagent.database.entity.ChecklistEntity

/**
 * A checklist plus its item counts, as returned by
 * [ChecklistDao.observeChecklistSummaries].
 *
 * A query result rather than a stored entity — there is no `summaries` table.
 */
data class ChecklistSummary(
    @Embedded val checklist: ChecklistEntity,

    @ColumnInfo(name = "item_count")
    val itemCount: Int,

    @ColumnInfo(name = "done_count")
    val doneCount: Int,
)
