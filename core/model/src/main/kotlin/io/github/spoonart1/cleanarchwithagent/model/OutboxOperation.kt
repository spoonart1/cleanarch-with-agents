package io.github.spoonart1.cleanarchwithagent.model

/** Which entity an outbox entry refers to. */
enum class OutboxEntityType {
    CHECKLIST,
    CHECKLIST_ITEM,
}

/** What to do with it on the server. */
enum class OutboxOperationType {
    CREATE,
    UPDATE,
    DELETE,
}

/**
 * One pending change waiting to be pushed to the server.
 *
 * [operationId] is generated when the entry is written and never changes, so
 * replaying an entry the server already applied is a no-op rather than a
 * duplicate. This is what makes retries safe: the worker can fail anywhere
 * between "server applied the change" and "local row marked synced", and the
 * retry will not create a second record.
 */
data class OutboxOperation(
    val operationId: String,
    val entityType: OutboxEntityType,
    val entityId: String,
    val operationType: OutboxOperationType,
    val createdAt: Long,
    val attemptCount: Int = 0,
)
