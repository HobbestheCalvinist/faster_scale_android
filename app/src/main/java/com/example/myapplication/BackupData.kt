package com.example.myapplication

data class BackupData(
    val checkIns: List<CheckIn> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val callSchedules: List<CallSchedule> = emptyList(),
    val commitments: List<Commitment> = emptyList(),
    val preferences: Map<String, *> = emptyMap<String, Any>()
)
