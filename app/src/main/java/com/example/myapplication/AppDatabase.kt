package com.example.myapplication

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

@Database(entities = [CheckIn::class, Contact::class, CallSchedule::class, Commitment::class], version = 13, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun checkInDao(): CheckInDao
    abstract fun contactDao(): ContactDao
    abstract fun callScheduleDao(): CallScheduleDao
    abstract fun commitmentDao(): CommitmentDao

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
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "faster_scale_demo_database"
                    )
                    .fallbackToDestructiveMigration()
                    .addCallback(DemoDatabaseCallback(context))
                    .build()
                    DEMO_INSTANCE = instance
                    instance
                }
            } else {
                INSTANCE ?: synchronized(this) {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "faster_scale_database"
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                    INSTANCE = instance
                    instance
                }
            }
        }

        private class DemoDatabaseCallback(private val context: Context) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                val scope = CoroutineScope(Dispatchers.IO)
                scope.launch {
                    val demoDb = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "faster_scale_demo_database"
                    ).build()
                    populateDemoData(demoDb)
                    demoDb.close()
                }
            }
        }

        private suspend fun populateDemoData(db: AppDatabase) {
            val contactDao = db.contactDao()
            val scheduleDao = db.callScheduleDao()
            val checkInDao = db.checkInDao()
            val commitmentDao = db.commitmentDao()

            // 1. Contacts
            val contact1Id = contactDao.insertContact(Contact(name = "John Sponsor", phoneNumber = "555-0101")).toInt()
            val contact2Id = contactDao.insertContact(Contact(name = "Sarah Support", phoneNumber = "555-0123")).toInt()

            // 2. Call Schedules
            scheduleDao.insertSchedule(CallSchedule(
                dayOfWeek = "Monday",
                time = "10:00 AM",
                contactId = contact1Id,
                contactName = "John Sponsor",
                contactPhone = "555-0101",
                isInbound = false
            ))
            scheduleDao.insertSchedule(CallSchedule(
                dayOfWeek = "Thursday",
                time = "07:00 PM",
                contactId = contact2Id,
                contactName = "Sarah Support",
                contactPhone = "555-0123",
                isInbound = true
            ))

            // 3. Commitments
            commitmentDao.insertCommitment(Commitment(
                title = "Daily Prayer",
                description = "Spend 10 minutes in prayer each morning",
                targetCompletions = 7,
                completedDaysMask = 31 // Monday to Friday
            ))
            commitmentDao.insertCommitment(Commitment(
                title = "Exercise",
                description = "Walk for 30 minutes",
                targetCompletions = 3,
                completedDaysMask = 10 // Tuesday and Thursday
            ))

            // 4. Check-ins (Past 10 days)
            val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
            
            val levels = listOf(
                "Restoration", "Restoration", "Forgetting Priorities", 
                "Anxiety", "Speeding Up", "Restoration", 
                "Restoration", "Exhausted", "Ticked Off", "Restoration"
            )

            for (i in 0 until 10) {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -i)
                val dateStr = sdf.format(cal.time)
                
                checkInDao.insertCheckIn(CheckIn(
                    date = dateStr,
                    scaleOption = levels[i % levels.size],
                    description = "Feeling ${levels[i % levels.size].lowercase()} today. Focusing on my commitments.",
                    callMade = i % 3 == 0,
                    isInboundCall = i % 6 == 0
                ))
            }
        }
    }
}
