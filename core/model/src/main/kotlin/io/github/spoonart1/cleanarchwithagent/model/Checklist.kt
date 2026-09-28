package io.github.spoonart1.cleanarchwithagent.model

/**
 * A field checklist.
 *
 * [id] is the local identifier and is generated on-device, so a checklist can be
 * created with no network. [serverId] is null until the server has accepted it.
 *
 * [updatedAt] is epoch milliseconds and drives last-write-wins conflict
 * resolution. It is stored rather than derived so that the value compared
 * against the server is the one recorded at edit time.
 */
data class Checklist(
    val id: String,
    val serverId: String? = null,
    val title: String,
    val updatedAt: Long,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)

/**
 * One line item on a [Checklist].
 *
 * Items carry their own [syncStatus] because they sync independently: a
 * checklist can be fully synced while one of its items is still pending.
 */
data class ChecklistItem(
    val id: String,
    val serverId: String? = null,
    val checklistId: String,
    val text: String,
    val isDone: Boolean = false,
    val note: String? = null,
    val updatedAt: Long,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)

/** A [Checklist] together with its items, as the detail screen needs it. */
data class ChecklistWithItems(
    val checklist: Checklist,
    val items: List<ChecklistItem>,
)

/**
 * A [Checklist] with its item counts, as a list screen needs it.
 *
 * The counts come from an aggregate query rather than from loading every
 * item, so showing a hundred checklists stays one query.
 */
data class ChecklistSummary(
    val checklist: Checklist,
    val itemCount: Int,
    val doneCount: Int,
)
