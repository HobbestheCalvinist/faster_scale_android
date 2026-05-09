package com.example.myapplication

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CheckIn::class, Contact::class, CallSchedule::class, Commitment::class], version = 10, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun checkInDao(): CheckInDao
    abstract fun contactDao(): ContactDao
    abstract fun callScheduleDao(): CallScheduleDao
    abstract fun commitmentDao(): CommitmentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "faster_scale_database"
                )
                // Removed fallbackToDestructiveMigration() to prevent accidental data loss.
                // In production, migrations should be provided.
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
