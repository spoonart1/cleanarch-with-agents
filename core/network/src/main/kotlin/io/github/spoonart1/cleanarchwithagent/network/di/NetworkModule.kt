package io.github.spoonart1.cleanarchwithagent.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.spoonart1.cleanarchwithagent.network.NetworkDataSource
import io.github.spoonart1.cleanarchwithagent.network.fake.FakeNetworkDataSource
import io.github.spoonart1.cleanarchwithagent.network.retrofit.ChecklistApi
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * The fake backend is the default binding, so a fresh clone runs with no
     * server and the network simulator can demonstrate offline behaviour.
     *
     * To talk to a real backend, return the injected [ChecklistApi]-backed
     * `RetrofitNetworkDataSource` here instead and set [BASE_URL].
     */
    @Provides
    @Singleton
    fun providesNetworkDataSource(
        fake: FakeNetworkDataSource,
    ): NetworkDataSource = fake

    @Provides
    @Singleton
    fun providesJson(): Json = Json {
        // Tolerate fields the app does not know about, so a server-side addition
        // does not crash an older client.
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun providesOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            },
        )
        .build()

    @Provides
    @Singleton
    fun providesRetrofit(
        json: Json,
        clientFactory: Call.Factory,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .callFactory(clientFactory)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun providesCallFactory(client: OkHttpClient): Call.Factory = client

    @Provides
    @Singleton
    fun providesChecklistApi(retrofit: Retrofit): ChecklistApi =
        retrofit.create(ChecklistApi::class.java)

    /**
     * Placeholder. The fake backend is wired by default, so nothing calls this
     * until someone points the template at a real server.
     */
    private const val BASE_URL = "https://example.invalid/api/"
}
