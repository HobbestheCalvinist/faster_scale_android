package com.fasterscale.app

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomFeelingDao {
    @Query("SELECT * FROM custom_feelings")
    fun getAllCustomFeelings(): Flow<List<CustomFeeling>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomFeeling(customFeeling: CustomFeeling)

    @Delete
    suspend fun deleteCustomFeeling(customFeeling: CustomFeeling)
}
