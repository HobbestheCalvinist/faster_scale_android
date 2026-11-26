package com.fasterscale.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Database(entities = [CheckIn::class, Contact::class, CallSchedule::class, Commitment::class, CustomFeeling::class], version = 24, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun checkInDao(): CheckInDao
    abstract fun contactDao(): ContactDao
    abstract fun callScheduleDao(): CallScheduleDao
    abstract fun commitmentDao(): CommitmentDao
    abstract fun customFeelingDao(): CustomFeelingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        @Volatile
        private var DEMO_INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val useDemo = prefs.getBoolean("demo_mode", false)

            return if (useDemo) {
                DEMO_INSTANCE ?: synchronized(this) {
                    DEMO_INSTANCE ?: buildDatabase(context, "faster_scale_demo_database", true).also {
                        DEMO_INSTANCE = it
                    }
                }
            } else {
                INSTANCE ?: synchronized(this) {
                    INSTANCE ?: buildDatabase(context, "faster_scale_database", false).also {
                        INSTANCE = it
                    }
                }
            }
        }

        private fun buildDatabase(context: Context, name: String, isDemo: Boolean): AppDatabase {
            val db = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                name
            )
            .fallbackToDestructiveMigration()
            .build()

            if (isDemo) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (db.contactDao().getContactCount() == 0) {
                            populateDemoData(db)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            return db
        }

        private suspend fun populateDemoData(db: AppDatabase) {
            val contactDao = db.contactDao()
            val scheduleDao = db.callScheduleDao()
            val checkInDao = db.checkInDao()
            val commitmentDao = db.commitmentDao()

            // 1. Contacts
            val c1Id = contactDao.insertContact(Contact(name = "John Sponsor", phoneNumber = "555-0101")).toInt()
            val c2Id = contactDao.insertContact(Contact(name = "Sarah Support", phoneNumber = "555-0123")).toInt()

            // 2. Call Schedules
            scheduleDao.insertSchedule(CallSchedule(
                dayOfWeek = "Monday", time = "10:00 AM",
                contactId = c1Id, contactName = "John Sponsor", contactPhone = "555-0101",
                isInbound = false
            ))
            scheduleDao.insertSchedule(CallSchedule(
                dayOfWeek = "Thursday", time = "07:00 PM",
                contactId = c2Id, contactName = "Sarah Support", contactPhone = "555-0123",
                isInbound = true
            ))

            // 3. Commitments
            commitmentDao.insertCommitment(Commitment(
                title = "Daily Prayer", description = "Spend 10 minutes in prayer each morning",
                frequency = "DAILY", interval = 1, targetCompletions = 1, completedCount = 1,
                status = 1, totalCompletions = 5, lastCompletionTimestamp = System.currentTimeMillis() - 86400000
            ))
            commitmentDao.insertCommitment(Commitment(
                title = "Exercise", description = "Walk for 30 minutes",
                frequency = "WEEKLY", interval = 1, targetCompletions = 1, completedCount = 0,
                status = 0, totalCompletions = 12
            ))

            // 4. Check-ins
            val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
            val levels = listOf("Restoration", "Anxiety", "Exhausted", "Restoration")
            for (i in 0 until 5) {
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
                checkInDao.insertCheckIn(CheckIn(
                    date = sdf.format(cal.time),
                    scaleOption = levels[i % levels.size],
                    description = "Feeling ${levels[i % levels.size].lowercase()} today.",
                    callMade = i % 2 == 0,
                    temptationLevel = i % 5
                ))
            }
        }
    }
}
