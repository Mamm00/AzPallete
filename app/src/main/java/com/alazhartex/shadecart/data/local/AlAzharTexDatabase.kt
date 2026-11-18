package com.alazhartex.shadecart.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        FabricType::class,
        Roll::class,
        ShadeCartEntity::class,
        SwatchEntity::class,
        SyncQueueEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AlAzharTexDatabase : RoomDatabase() {
    abstract fun fabricTypeDao(): FabricTypeDao
    abstract fun rollDao(): RollDao
    abstract fun shadeCartDao(): ShadeCartDao
    abstract fun swatchDao(): SwatchDao
    abstract fun syncQueueDao(): SyncQueueDao
}
