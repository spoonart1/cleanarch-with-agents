package io.github.spoonart1.cleanarchwithagent.network

import io.github.spoonart1.cleanarchwithagent.network.model.PushRequest
import io.github.spoonart1.cleanarchwithagent.network.model.PushResponse
import io.github.spoonart1.cleanarchwithagent.network.model.SyncResponse

/**
 * The seam between the app and the server.
 *
 * Two implementations exist: a real Retrofit one, and a fake in-app backend
 * which is the default so the template runs with no server. Everything above
 * this interface is identical either way.
 */
interface NetworkDataSource {

    /**
     * Fetches everything that changed since [syncToken].
     *
     * A null token means a full sync (first run, or after the local database
     * was cleared).
     */
    suspend fun pull(syncToken: String?): SyncResponse

    /**
     * Pushes one queued change.
     *
     * Must be idempotent with respect to [PushRequest.operationId]: replaying a
     * request the server already applied returns the original result rather
     * than creating a duplicate.
     */
    suspend fun push(request: PushRequest): PushResponse
}
