package io.github.spoonart1.cleanarchwithagent.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.spoonart1.cleanarchwithagent.database.CleanArchDatabase
import io.github.spoonart1.cleanarchwithagent.database.dao.ChecklistDao
import io.github.spoonart1.cleanarchwithagent.database.dao.OutboxDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providesDatabase(
        @ApplicationContext context: Context,
    ): CleanArchDatabase = Room.databaseBuilder(
        context,
        CleanArchDatabase::class.java,
        "cleanarch.db",
    ).build()

    @Provides
    fun providesChecklistDao(database: CleanArchDatabase): ChecklistDao =
        database.checklistDao()

    @Provides
    fun providesOutboxDao(database: CleanArchDatabase): OutboxDao =
        database.outboxDao()
}
