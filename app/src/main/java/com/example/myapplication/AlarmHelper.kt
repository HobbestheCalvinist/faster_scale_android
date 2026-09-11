package com.fasterscale.app

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import java.util.*

object AlarmHelper {

    fun canScheduleExact(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun scheduleCallAlarm(context: Context, schedule: CallSchedule) {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("phone_call_reminder_enabled", false)) return
        if (schedule.time == "Not set" || schedule.time.isBlank()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val eventCalendar = getCalendarForSchedule(schedule) ?: return
        val reminderCalendar = (eventCalendar.clone() as Calendar).apply {
            add(Calendar.MINUTE, -15)
        }

        if (reminderCalendar.timeInMillis <= System.currentTimeMillis()) {
            if (eventCalendar.timeInMillis <= System.currentTimeMillis()) {
                reminderCalendar.add(Calendar.WEEK_OF_YEAR, 1)
            } else {
                reminderCalendar.timeInMillis = System.currentTimeMillis() + 1000
            }
        }

        val intent = Intent(context, CallAlarmReceiver::class.java).apply {
            putExtra("scheduleId", schedule.id)
            putExtra("contactName", schedule.contactName)
            putExtra("contactPhone", schedule.contactPhone)
            putExtra("isInbound", schedule.isInbound)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            2000 + schedule.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setExactAlarm(alarmManager, reminderCalendar.timeInMillis, pendingIntent)
    }

    fun scheduleDailyReminder(context: Context, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("reminder_enabled", false)) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 1000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
            }
        }

        setExactAlarm(alarmManager, calendar.timeInMillis, pendingIntent)
    }

    private fun setExactAlarm(alarmManager: AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancelCallAlarm(context: Context, scheduleId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, CallAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 2000 + scheduleId, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    fun cancelDailyReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 1000, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun getCalendarForSchedule(schedule: CallSchedule): Calendar? {
        if (schedule.time == "Not set" || schedule.time.isBlank()) return null
        
        try {
            val calendar = Calendar.getInstance()
            val dayOfWeek = when (schedule.dayOfWeek.trim()) {
                "Sunday" -> Calendar.SUNDAY
                "Monday" -> Calendar.MONDAY
                "Tuesday" -> Calendar.TUESDAY
                "Wednesday" -> Calendar.WEDNESDAY
                "Thursday" -> Calendar.THURSDAY
                "Friday" -> Calendar.FRIDAY
                "Saturday" -> Calendar.SATURDAY
                else -> Calendar.MONDAY
            }

            val time = schedule.time.uppercase()
            val isPm = time.endsWith("PM")
            val timeParts = time.replace("AM", "").replace("PM", "").trim().split(":")
            
            if (timeParts.size < 2) return null
            
            var hour = timeParts[0].trim().toInt()
            val minute = timeParts[1].trim().toInt()

            if (isPm && hour != 12) hour += 12
            if (!isPm && hour == 12) hour = 0

            calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek)
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            return calendar
        } catch (e: Exception) {
            return null
        }
    }
}
