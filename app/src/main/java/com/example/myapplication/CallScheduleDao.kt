package com.fasterscale.app

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CallScheduleDao {
    @Query("""
        SELECT * FROM call_schedules 
        ORDER BY 
            CASE dayOfWeek 
                WHEN 'Monday' THEN 1 
                WHEN 'Tuesday' THEN 2 
                WHEN 'Wednesday' THEN 3 
                WHEN 'Thursday' THEN 4 
                WHEN 'Friday' THEN 5 
                WHEN 'Saturday' THEN 6 
                WHEN 'Sunday' THEN 7 
                ELSE 8 
            END,
            SUBSTR(time, 7, 2) ASC, 
            CASE WHEN SUBSTR(time, 1, 2) = '12' THEN '00' ELSE SUBSTR(time, 1, 2) END ASC, 
            SUBSTR(time, 4, 2) ASC
    """)
    fun getAllSchedules(): Flow<List<CallSchedule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: CallSchedule): Long

    @Update
    suspend fun updateSchedule(schedule: CallSchedule)

    @Delete
    suspend fun deleteSchedule(schedule: CallSchedule)
}
