package com.example.osmastic.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
//import androidx.room.TypeConverters
import android.content.Context

@Database(
    entities = [Pin::class, ToBeRenderedPin::class],
    version = 1,
    autoMigrations = [  // MIGRATIONS (like adoing a new field, nothing fancy
        // Auto migrations for future versions
        // AutoMigration(from = 1, to = 2),
        // AutoMigration(from = 2, to = 3),
    ],
    exportSchema = true  // what is it???
)
//@TypeConverters(Converters::class)  // We'll need converters for ByteArray etc.
abstract class AppDatabase : RoomDatabase() {
    abstract fun pinDao(): PinDao
    abstract fun winnerDao(): ToBeRenderedPinDao
    // add like this: abstract fun channelDao(): ChannelDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "osmastic_global_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}