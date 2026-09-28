package io.github.spoonart1.cleanarchwithagent.network.retrofit

import io.github.spoonart1.cleanarchwithagent.network.NetworkDataSource
import io.github.spoonart1.cleanarchwithagent.network.model.PushRequest
import io.github.spoonart1.cleanarchwithagent.network.model.PushResponse
import io.github.spoonart1.cleanarchwithagent.network.model.SyncResponse
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** The HTTP surface of the real backend. */
interface ChecklistApi {

    @GET("sync")
    suspend fun pull(@Query("since") syncToken: String?): SyncResponse

    @POST("push")
    suspend fun push(@Body request: PushRequest): PushResponse
}

/**
 * The real implementation, kept deliberately thin.
 *
 * It is not the default — [io.github.spoonart1.cleanarchwithagent.network.fake.FakeNetworkDataSource]
 * is, so the template runs with no server. Point `BASE_URL` at a real backend
 * and swap the binding in `NetworkModule` to use this instead.
 */
@Singleton
class RetrofitNetworkDataSource @Inject constructor(
    private val api: ChecklistApi,
) : NetworkDataSource {

    override suspend fun pull(syncToken: String?): SyncResponse = api.pull(syncToken)

    override suspend fun push(request: PushRequest): PushResponse = api.push(request)
}
