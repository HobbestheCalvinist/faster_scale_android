package com.fasterscale.app

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
        val isInbound = intent.getBooleanExtra("isInbound", false)

        showNotification(context, contactName, contactPhone, scheduleId, isInbound)
    }

    private fun showNotification(context: Context, contactName: String, contactPhone: String, scheduleId: Int, isInbound: Boolean) {
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

        // Intent to open the app directly to the Call Schedule tab
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(MainTabsFragment.EXTRA_OPEN_TAB, 2) // Index for Call Schedule tab
        }
        val pendingIntent = PendingIntent.getActivity(
            context, scheduleId, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to make the call directly
        val callIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$contactPhone")
        }
        val callPendingIntent = PendingIntent.getActivity(
            context, scheduleId + 10000, callIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val iconRes = if (isInbound) R.drawable.ic_call_inbound else R.drawable.ic_call_outbound
        val contentText = if (isInbound) {
            context.getString(R.string.notification_inbound_text, contactName)
        } else {
            context.getString(R.string.notification_outbound_text, contactName)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle(context.getString(R.string.notification_call_title))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_call, context.getString(R.string.settings_test_call_now), callPendingIntent)
            .addAction(android.R.drawable.ic_menu_agenda, context.getString(R.string.call_schedule_title), pendingIntent)

        manager.notify(notificationId, builder.build())
    }

    private fun Intent.getIntOf(name: String, default: Int): Int = getIntExtra(name, default)
}
