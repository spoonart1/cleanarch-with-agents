package io.github.spoonart1.cleanarchwithagent.model

/**
 * Where a record stands relative to the server.
 *
 * Every syncable entity carries one of these. The UI shows it so a user can
 * tell what has actually reached the server, which is the whole point of an
 * offline-first app: edits are never silently lost, and never silently pending.
 */
enum class SyncStatus {
    /** Matches the server as of the last successful sync. */
    SYNCED,

    /** Changed locally and waiting in the outbox to be pushed. */
    PENDING,

    /** Pushing failed. The outbox will retry with backoff. */
    FAILED,

    /**
     * The server changed this record while a local change was still pending.
     * Last-write-wins is deliberately NOT applied here — the local edit is kept
     * and the record is flagged so it can be resolved rather than overwritten.
     */
    CONFLICT,
}
