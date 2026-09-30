package com.apppulse.sdk.internal.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.apppulse.sdk.internal.logging.SdkLogger

/**
 * Room database for the AppPulse SDK.
 *
 * Uses the double-checked locking singleton pattern to ensure only one
 * database instance exists. The database is created lazily on first access.
 *
 * Schema versioning starts at 1. When the schema changes in a future version,
 * a migration will be added rather than using destructive migration.
 */
@Database(
    entities = [AnalyticsEventEntity::class],
    version = 1,
    exportSchema = false
)
internal abstract class AppPulseDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    companion object {
        private const val DB_NAME = "apppulse_analytics.db"

        @Volatile
        private var INSTANCE: AppPulseDatabase? = null

        /**
         * Returns the singleton database instance, creating it if needed.
         * Thread-safe via double-checked locking.
         */
        fun getInstance(context: Context): AppPulseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also {
                    INSTANCE = it
                    SdkLogger.d("Database created: $DB_NAME")
                }
            }
        }

        private fun buildDatabase(context: Context): AppPulseDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppPulseDatabase::class.java,
                DB_NAME
            ).build()
        }

        /** Resets the singleton — for testing only. */
        internal fun destroyInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
