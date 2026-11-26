package com.fasterscale.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        showNotification(context)
        
        // AUTO-RESCHEDULE: Alarms are one-shot. 
        // We must schedule the next one for tomorrow immediately.
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("reminder_enabled", false)) {
            val h = prefs.getInt("reminder_hour", 8)
            val m = prefs.getInt("reminder_minute", 0)
            AlarmHelper.scheduleDailyReminder(context, h, m)
        }
    }

    private fun showNotification(context: Context) {
        val channelId = "faster_scale_reminder_channel"
        val notificationId = 1001

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Daily Check-in Reminder",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val checkInIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(MainTabsFragment.EXTRA_OPEN_TAB, 0)
            putExtra(MainTabsFragment.EXTRA_SHOW_CHECKIN, true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 1, checkInIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Faster Scale Recovery")
            .setContentText("It's time for your daily check-in!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_edit, "Check In Now", pendingIntent)

        manager.notify(notificationId, builder.build())
    }
}
