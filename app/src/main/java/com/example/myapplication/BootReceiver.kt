package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val scope = CoroutineScope(Dispatchers.IO)
            
            // Reschedule Daily Reminder
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("reminder_enabled", false)) {
                val h = prefs.getInt("reminder_hour", 8)
                val m = prefs.getInt("reminder_minute", 0)
                AlarmHelper.scheduleDailyReminder(context, h, m)
            }

            // Reschedule all Call Alarms
            scope.launch {
                val db = AppDatabase.getDatabase(context)
                val schedules = db.callScheduleDao().getAllSchedules().first()
                schedules.forEach { schedule ->
                    AlarmHelper.scheduleCallAlarm(context, schedule)
                }
            }
        }
    }
}
