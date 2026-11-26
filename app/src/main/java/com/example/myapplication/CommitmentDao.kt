package com.fasterscale.app

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CommitmentDao {
    @Query("SELECT * FROM commitments WHERE status = 0 ORDER BY timestamp DESC")
    fun getActiveCommitments(): Flow<List<Commitment>>

    @Query("SELECT * FROM commitments WHERE status = 1 ORDER BY timestamp DESC")
    fun getPendingCommitments(): Flow<List<Commitment>>

    @Query("SELECT * FROM commitments WHERE status = 2 ORDER BY timestamp DESC")
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
