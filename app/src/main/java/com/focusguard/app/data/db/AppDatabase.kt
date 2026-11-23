package com.focusguard.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Local SQLite database (via Room). Bump [version] and add a Migration whenever
 * the schema changes — never use destructive migration once users have data.
 */
@Database(
    entities = [
        AppLimitEntity::class,
        FocusSessionEntity::class,
        BlockEventEntity::class,
        DailyUsageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appLimitDao(): AppLimitDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun blockEventDao(): BlockEventDao
    abstract fun dailyUsageDao(): DailyUsageDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focusguard.db",
                ).build().also { instance = it }
            }
    }
}
