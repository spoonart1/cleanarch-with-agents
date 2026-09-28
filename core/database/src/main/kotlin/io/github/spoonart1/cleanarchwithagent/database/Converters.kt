package io.github.spoonart1.cleanarchwithagent.database

import androidx.room.TypeConverter
import io.github.spoonart1.cleanarchwithagent.model.OutboxEntityType
import io.github.spoonart1.cleanarchwithagent.model.OutboxOperationType
import io.github.spoonart1.cleanarchwithagent.model.SyncStatus

/**
 * Enums are stored by name, not ordinal.
 *
 * Ordinals would silently remap every stored row if someone reordered an enum
 * constant; names fail loudly instead.
 */
class Converters {

    @TypeConverter
    fun syncStatusToString(value: SyncStatus): String = value.name

    @TypeConverter
    fun stringToSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter
    fun outboxEntityTypeToString(value: OutboxEntityType): String = value.name

    @TypeConverter
    fun stringToOutboxEntityType(value: String): OutboxEntityType =
        OutboxEntityType.valueOf(value)

    @TypeConverter
    fun outboxOperationTypeToString(value: OutboxOperationType): String = value.name

    @TypeConverter
    fun stringToOutboxOperationType(value: String): OutboxOperationType =
        OutboxOperationType.valueOf(value)
}
