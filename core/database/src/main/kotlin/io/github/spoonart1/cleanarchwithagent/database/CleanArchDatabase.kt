package io.github.spoonart1.cleanarchwithagent.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.spoonart1.cleanarchwithagent.database.dao.ChecklistDao
import io.github.spoonart1.cleanarchwithagent.database.dao.OutboxDao
import io.github.spoonart1.cleanarchwithagent.database.entity.ChecklistEntity
import io.github.spoonart1.cleanarchwithagent.database.entity.ChecklistItemEntity
import io.github.spoonart1.cleanarchwithagent.database.entity.OutboxEntity

/**
 * The single source of truth for the app.
 *
 * Schemas are exported to `core/database/schemas` by the Room convention
 * plugin, so a schema change shows up as a reviewable diff in the pull request
 * and migration tests have something to run against.
 */
@Database(
    entities = [
        ChecklistEntity::class,
        ChecklistItemEntity::class,
        OutboxEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CleanArchDatabase : RoomDatabase() {
    abstract fun checklistDao(): ChecklistDao
    abstract fun outboxDao(): OutboxDao
}
