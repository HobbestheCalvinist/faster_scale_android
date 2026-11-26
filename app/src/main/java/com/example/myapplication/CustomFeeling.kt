package com.fasterscale.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_feelings")
data class CustomFeeling(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val emoji: String,
    val label: String,
    val levelId: String
)
