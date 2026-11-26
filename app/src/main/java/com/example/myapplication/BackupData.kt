package com.fasterscale.app

data class BackupData(
    val checkIns: List<CheckInDTO>? = null,
    val contacts: List<ContactDTO>? = null,
    val callSchedules: List<CallScheduleDTO>? = null,
    val commitments: List<CommitmentDTO>? = null,
    val customFeelings: List<CustomFeelingDTO>? = null,
    val preferences: Map<String, *>? = null
)

data class CheckInDTO(
    val id: Int = 0,
    val date: String? = null,
    val scaleOption: String? = null,
    val feeling: String? = null,
    val feelingEmoji: String? = null,
    val feelingIntensity: Int? = null,
    val description: String? = null,
    val callMade: Boolean? = null,
    val isInboundCall: Boolean? = null,
    val completedScheduleIds: String? = null,
    val liedToday: Boolean? = null,
    val workingCommitments: Boolean? = null,
    val temptationLevel: Int? = null
)

data class ContactDTO(
    val id: Int = 0,
    val name: String? = null,
    val phoneNumber: String? = null
)

data class CallScheduleDTO(
    val id: Int = 0,
    val dayOfWeek: String? = null,
    val time: String? = null,
    val contactId: Int? = null,
    val contactName: String? = null,
    val contactPhone: String? = null,
    val isInbound: Boolean? = null
)

data class CommitmentDTO(
    val id: Int = 0,
    val title: String? = null,
    val description: String? = null,
    val frequency: String? = null,
    val interval: Int? = null,
    val daysOfWeekBitmask: Int? = null,
    val dayOfMonth: Int? = null,
    val targetCompletions: Int? = null,
    val currentCompletions: Int? = null, // Legacy field
    val completedDaysMask: Int? = null,
    val completedCount: Int? = null,
    val totalCompletions: Int? = null,
    val isCompleted: Boolean? = null,
    val lastCompletionTimestamp: Long? = null,
    val timestamp: Long? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val status: Int? = null
)

data class CustomFeelingDTO(
    val id: Int = 0,
    val emoji: String? = null,
    val label: String? = null,
    val levelId: String? = null,
    // Legacy fields for migration
    val category: String? = null,
    val meaning: String? = null
)
