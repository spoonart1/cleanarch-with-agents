package io.github.spoonart1.cleanarchwithagent.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.spoonart1.cleanarchwithagent.data.ChecklistRepository
import io.github.spoonart1.cleanarchwithagent.data.OfflineFirstChecklistRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindsChecklistRepository(
        impl: OfflineFirstChecklistRepository,
    ): ChecklistRepository
}
