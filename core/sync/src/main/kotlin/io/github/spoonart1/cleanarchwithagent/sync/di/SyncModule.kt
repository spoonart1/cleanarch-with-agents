package io.github.spoonart1.cleanarchwithagent.sync.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.spoonart1.cleanarchwithagent.sync.PreferencesSyncTokenStore
import io.github.spoonart1.cleanarchwithagent.sync.SyncScheduler
import io.github.spoonart1.cleanarchwithagent.sync.SyncTokenStore
import io.github.spoonart1.cleanarchwithagent.sync.WorkManagerSyncScheduler
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    @Singleton
    abstract fun bindsSyncTokenStore(impl: PreferencesSyncTokenStore): SyncTokenStore

    @Binds
    @Singleton
    abstract fun bindsSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler
}
