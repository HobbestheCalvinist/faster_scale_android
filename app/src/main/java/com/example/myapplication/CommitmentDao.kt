package com.fasterscale.app

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CommitmentDao {
    @Query("SELECT * FROM commitments WHERE isCompleted = 0")
    fun getActiveCommitments(): Flow<List<Commitment>>

    @Query("SELECT * FROM commitments WHERE isCompleted = 1")
    fun getCompletedCommitments(): Flow<List<Commitment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommitment(commitment: Commitment)

    @Update
    suspend fun updateCommitment(commitment: Commitment)

    @Delete
    suspend fun deleteCommitment(commitment: Commitment)

    @Query("DELETE FROM commitments")
    suspend fun deleteAll()
}
