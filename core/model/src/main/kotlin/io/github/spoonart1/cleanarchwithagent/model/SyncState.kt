package io.github.spoonart1.cleanarchwithagent.model

/**
 * What the sync engine is doing right now, exposed to the UI as a Flow.
 *
 * [pendingCount] is carried on every state, not just [Idle], so a screen can
 * show "3 pending" while a sync is in flight without combining two flows.
 */
sealed interface SyncState {

    val pendingCount: Int

    /** Nothing in flight. [pendingCount] of 0 means everything reached the server. */
    data class Idle(override val pendingCount: Int = 0) : SyncState

    /** A sync is running. */
    data class Syncing(override val pendingCount: Int = 0) : SyncState

    /**
     * The last sync failed. Pending work is not lost — it stays in the outbox
     * and is retried with backoff.
     */
    data class Error(
        val message: String,
        override val pendingCount: Int = 0,
    ) : SyncState
}
