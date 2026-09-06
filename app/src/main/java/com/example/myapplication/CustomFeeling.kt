package com.fasterscale.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_feelings")
data class CustomFeeling(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String,
    val emoji: String,
    val meaning: String
)
