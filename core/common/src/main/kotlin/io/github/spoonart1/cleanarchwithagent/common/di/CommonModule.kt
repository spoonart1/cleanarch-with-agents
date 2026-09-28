package io.github.spoonart1.cleanarchwithagent.common.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.spoonart1.cleanarchwithagent.common.Clock
import io.github.spoonart1.cleanarchwithagent.common.IdGenerator
import io.github.spoonart1.cleanarchwithagent.common.SystemClock
import io.github.spoonart1.cleanarchwithagent.common.UuidGenerator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CommonModule {

    @Binds
    @Singleton
    abstract fun bindsClock(impl: SystemClock): Clock

    @Binds
    @Singleton
    abstract fun bindsIdGenerator(impl: UuidGenerator): IdGenerator
}
