package com.fasterscale.app

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "commitments")
data class Commitment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String = "",
    val description: String = "",
    val frequency: String = "WEEKLY", // DAILY, WEEKLY, FLEX_WEEKLY, FLEX_MONTHLY, MONTHLY
    val interval: Int = 1,
    val daysOfWeekBitmask: Int = 0,  // Unused for now as per requirements
    val dayOfMonth: Int = 1,
    val targetCompletions: Int = 1,
    val completedCount: Int = 0,    // Successful iterations in current period
    val totalCompletions: Int = 0,  // Lifetime successful iterations
    val lastCompletionTimestamp: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null,      // Date after which the task no longer re-enables
    val status: Int = 0             // 0: Active, 1: Pending (Done for now), 2: Completed Forever
) {
    /**
     * Determines if the commitment has entered a new iteration period and should be reset to Active.
     */
    fun shouldReset(currentTime: Long, startDayOfWeek: Int): Boolean {
        if (status != 1) return false // Only Pending items reset to Active
        
        // If end date is set and we've passed it, it should have been moved to status 2
        if (endDate != null && currentTime > endDate) return false

        val referenceTime = if (lastCompletionTimestamp > 0) lastCompletionTimestamp else startDate
        
        val refCal = Calendar.getInstance().apply { 
            firstDayOfWeek = startDayOfWeek
            timeInMillis = referenceTime 
        }
        val currentCal = Calendar.getInstance().apply { 
            firstDayOfWeek = startDayOfWeek
            timeInMillis = currentTime 
        }

        return when (frequency) {
            "DAILY" -> getDaysBetween(refCal, currentCal) >= interval
            "WEEKLY", "FLEX_WEEKLY" -> getWeeksBetween(refCal, currentCal, startDayOfWeek) >= interval
            "MONTHLY", "FLEX_MONTHLY" -> {
                val monthsDiff = (currentCal.get(Calendar.YEAR) - refCal.get(Calendar.YEAR)) * 12 + 
                                 currentCal.get(Calendar.MONTH) - refCal.get(Calendar.MONTH)
                monthsDiff >= interval
            }
            else -> false
        }
    }

    private fun getDaysBetween(start: Calendar, end: Calendar): Int {
        val s = start.clone() as Calendar
        s.set(Calendar.HOUR_OF_DAY, 0); s.set(Calendar.MINUTE, 0); s.set(Calendar.SECOND, 0); s.set(Calendar.MILLISECOND, 0)
        val e = end.clone() as Calendar
        e.set(Calendar.HOUR_OF_DAY, 0); e.set(Calendar.MINUTE, 0); e.set(Calendar.SECOND, 0); e.set(Calendar.MILLISECOND, 0)
        return ((e.timeInMillis - s.timeInMillis) / (24 * 60 * 60 * 1000L)).toInt()
    }

    private fun getWeeksBetween(start: Calendar, end: Calendar, firstDay: Int): Int {
        val s = start.clone() as Calendar
        s.firstDayOfWeek = firstDay
        s.set(Calendar.DAY_OF_WEEK, firstDay)
        s.set(Calendar.HOUR_OF_DAY, 0); s.set(Calendar.MINUTE, 0); s.set(Calendar.SECOND, 0); s.set(Calendar.MILLISECOND, 0)
        
        val e = end.clone() as Calendar
        e.firstDayOfWeek = firstDay
        e.set(Calendar.DAY_OF_WEEK, firstDay)
        e.set(Calendar.HOUR_OF_DAY, 0); e.set(Calendar.MINUTE, 0); e.set(Calendar.SECOND, 0); e.set(Calendar.MILLISECOND, 0)
        
        return ((e.timeInMillis - s.timeInMillis) / (7 * 24 * 60 * 60 * 1000L)).toInt()
    }
        
    fun getScheduleDescription(): String {
        val unit = when (frequency) {
            "DAILY" -> if (interval == 1) "day" else "days"
            "WEEKLY", "FLEX_WEEKLY" -> if (interval == 1) "week" else "weeks"
            "MONTHLY", "FLEX_MONTHLY" -> if (interval == 1) "month" else "months"
            else -> ""
        }
        
        val every = if (interval == 1) "Every" else "Every $interval"
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val startStr = sdf.format(Date(startDate))
        val endStr = endDate?.let { " until ${sdf.format(Date(it))}" } ?: ""
        
        return when (frequency) {
            "WEEKLY" -> "Weekly (started $startStr)$endStr"
            "DAILY" -> "$every $unit (started $startStr)$endStr"
            "FLEX_WEEKLY" -> "Flexible Weekly ($targetCompletions times)$endStr"
            "FLEX_MONTHLY" -> "Flexible Monthly ($targetCompletions times)$endStr"
            "MONTHLY" -> "$every $unit on day $dayOfMonth (started $startStr)$endStr"
            else -> "$every $unit$endStr"
        }
    }

    fun getStatusDescription(): String {
        val lastStr = if (lastCompletionTimestamp == 0L) "Never" else java.text.SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(lastCompletionTimestamp))
        return "Last: $lastStr | Lifetime successes: $totalCompletions"
    }
}
