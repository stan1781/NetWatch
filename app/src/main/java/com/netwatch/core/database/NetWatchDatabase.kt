package com.netwatch.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DailyUsageEntity::class,
        AttributedDeltaEntity::class,
        AlertEventEntity::class,
        HotspotSessionEntity::class,
        SimIdentityEntity::class,
        SimChangeEventEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class NetWatchDatabase : RoomDatabase() {
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun attributedDeltaDao(): AttributedDeltaDao
    abstract fun alertEventDao(): AlertEventDao
    abstract fun hotspotSessionDao(): HotspotSessionDao
    abstract fun simIdentityDao(): SimIdentityDao
    abstract fun simChangeEventDao(): SimChangeEventDao

    companion object {
        fun create(context: Context): NetWatchDatabase =
            Room.databaseBuilder(context, NetWatchDatabase::class.java, "netwatch.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
