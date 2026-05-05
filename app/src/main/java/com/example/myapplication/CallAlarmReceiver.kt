package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat

class CallAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val contactName = intent.getStringExtra("contactName") ?: "Someone"
        val contactPhone = intent.getStringExtra("contactPhone") ?: ""
        val scheduleId = intent.getIntOf("scheduleId", 0)

        showNotification(context, contactName, contactPhone, scheduleId)
        
        // Reschedule for next week
        // Note: For production apps, you might want to call the AlarmHelper here
        // But we'll handle initial scheduling in the Fragment.
    }

    private fun showNotification(context: Context, contactName: String, contactPhone: String, scheduleId: Int) {
        val channelId = "call_schedule_channel"
        val notificationId = 2000 + scheduleId

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Call Schedule Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        // Intent to open the app
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, scheduleId, activityIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to make the call directly
        val callIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$contactPhone")
        }
        val callPendingIntent = PendingIntent.getActivity(
            context, scheduleId + 10000, callIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle("Upcoming Scheduled Call")
            .setContentText("Your call with $contactName is in 15 minutes.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_call, "Call Now", callPendingIntent)

        manager.notify(notificationId, builder.build())
    }

    private fun Intent.getIntOf(name: String, default: Int): Int = getIntExtra(name, default)
}
