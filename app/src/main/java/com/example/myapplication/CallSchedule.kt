package com.example.myapplication

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_schedules")
data class CallSchedule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dayOfWeek: String, // e.g., "Monday"
    val time: String,      // e.g., "10:00 AM"
    val contactId: Int,
    val contactName: String,
    val contactPhone: String
)
