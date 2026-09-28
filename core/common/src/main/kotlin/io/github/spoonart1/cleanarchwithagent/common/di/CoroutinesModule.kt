package io.github.spoonart1.cleanarchwithagent.common.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.spoonart1.cleanarchwithagent.common.ApplicationScope
import io.github.spoonart1.cleanarchwithagent.common.DefaultDispatcher
import io.github.spoonart1.cleanarchwithagent.common.IoDispatcher
import io.github.spoonart1.cleanarchwithagent.common.MainDispatcher
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    @Provides
    @IoDispatcher
    fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @MainDispatcher
    fun providesMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    /**
     * A scope that lives as long as the process.
     *
     * [SupervisorJob] so one failed child does not cancel the others — a failed
     * sync must not tear down unrelated background work.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun providesApplicationScope(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
}
