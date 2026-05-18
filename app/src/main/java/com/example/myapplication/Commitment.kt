package com.example.myapplication

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "commitments")
data class Commitment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String = "",
    val description: String = "",
    val targetCompletions: Int = 1,
    val completedDaysMask: Int = 0, // Bitmask for 7 days (0-6)
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    val currentCompletionsCount: Int
        get() = Integer.bitCount(completedDaysMask)
}
