package com.example.myapplication

data class BackupData(
    val checkIns: List<CheckIn>,
    val contacts: List<Contact>,
    val callSchedules: List<CallSchedule>,
    val commitments: List<Commitment>,
    val preferences: Map<String, *>
)
