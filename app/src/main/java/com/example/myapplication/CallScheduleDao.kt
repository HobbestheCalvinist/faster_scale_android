package com.example.myapplication

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CallScheduleDao {
    @Query("SELECT * FROM call_schedules")
    fun getAllSchedules(): Flow<List<CallSchedule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: CallSchedule)

    @Update
    suspend fun updateSchedule(schedule: CallSchedule)

    @Delete
    suspend fun deleteSchedule(schedule: CallSchedule)
}
