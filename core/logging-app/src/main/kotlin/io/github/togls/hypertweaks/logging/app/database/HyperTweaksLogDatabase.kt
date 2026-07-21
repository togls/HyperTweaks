package io.github.togls.hypertweaks.logging.app.database

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@Database(
    entities = [LogEntryEntity::class, LogSessionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class HyperTweaksLogDatabase : RoomDatabase() {
    abstract fun logEntryDao(): LogEntryDao

    abstract fun logSessionDao(): LogSessionDao

    companion object {
        const val DatabaseName = "hypertweaks_logs.db"

        fun open(context: Context): HyperTweaksLogDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                HyperTweaksLogDatabase::class.java,
                DatabaseName,
            ).setDriver(BundledSQLiteDriver())
                .build()
        }
    }
}
