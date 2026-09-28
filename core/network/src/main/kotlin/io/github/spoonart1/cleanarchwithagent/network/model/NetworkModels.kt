package io.github.spoonart1.cleanarchwithagent.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire models.
 *
 * Deliberately separate from the domain models in `core:model`: the server's
 * shape should be free to change without forcing a change to the app's domain,
 * and `core:data` owns the mapping between the two.
 */
@Serializable
data class NetworkChecklist(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted") val deleted: Boolean = false,
)

@Serializable
data class NetworkChecklistItem(
    @SerialName("id") val id: String,
    @SerialName("checklist_id") val checklistId: String,
    @SerialName("text") val text: String,
    @SerialName("done") val isDone: Boolean = false,
    @SerialName("note") val note: String? = null,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted") val deleted: Boolean = false,
)

/**
 * Everything that changed on the server since a given point.
 *
 * [syncToken] is echoed back on the next pull so the server can send only what
 * changed. Storing it rather than a local timestamp avoids clock-skew bugs
 * between device and server.
 */
@Serializable
data class SyncResponse(
    @SerialName("checklists") val checklists: List<NetworkChecklist> = emptyList(),
    @SerialName("items") val items: List<NetworkChecklistItem> = emptyList(),
    @SerialName("sync_token") val syncToken: String,
)

/**
 * A change being pushed to the server.
 *
 * [operationId] makes the request idempotent: a server that has already applied
 * this operation returns the same result instead of applying it twice.
 */
@Serializable
data class PushRequest(
    @SerialName("operation_id") val operationId: String,
    @SerialName("operation") val operation: String,
    @SerialName("checklist") val checklist: NetworkChecklist? = null,
    @SerialName("item") val item: NetworkChecklistItem? = null,
)

@Serializable
data class PushResponse(
    @SerialName("operation_id") val operationId: String,
    @SerialName("server_id") val serverId: String,
    @SerialName("updated_at") val updatedAt: Long,
)
