package com.example.myapplication

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "check_ins",
    indices = [Index(value = ["date"], unique = true)]
)
data class CheckIn(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String = "",
    val scaleOption: String = "",
    val description: String = "",
    val callMade: Boolean = false,
    val isInboundCall: Boolean = false,
    val completedScheduleIds: String = ""
)
