package com.example.myapplication

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_schedules")
data class CallSchedule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dayOfWeek: String = "",
    val time: String = "",
    val contactId: Int = 0,
    val contactName: String = "",
    val contactPhone: String = "",
    val isInbound: Boolean = false
)
