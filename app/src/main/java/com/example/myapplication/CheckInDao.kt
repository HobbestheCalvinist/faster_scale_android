package com.example.myapplication

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_ins ORDER BY id DESC")
    fun getAllCheckIns(): Flow<List<CheckIn>>

    @Query("SELECT * FROM check_ins WHERE date = :date LIMIT 1")
    suspend fun getCheckInByDate(date: String): CheckIn?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckIn(checkIn: CheckIn)

    @Update
    suspend fun updateCheckIn(checkIn: CheckIn)

    @Delete
    suspend fun deleteCheckIn(checkIn: CheckIn)
}
