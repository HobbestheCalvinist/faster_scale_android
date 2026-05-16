package com.example.myapplication

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

class QuickActionsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.quick_actions_widget)
        val iconTint = ContextCompat.getColor(context, R.color.widget_accent)

        // Check-in Intent
        val checkInIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(MainTabsFragment.EXTRA_OPEN_TAB, 0)
            putExtra(MainTabsFragment.EXTRA_SHOW_CHECKIN, true)
        }
        val checkInPI = PendingIntent.getActivity(context, 101, checkInIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.button_checkin, checkInPI)
        views.setInt(R.id.icon_checkin, "setColorFilter", iconTint)

        // Call Intent
        val callIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(MainTabsFragment.EXTRA_OPEN_TAB, 2)
        }
        val callPI = PendingIntent.getActivity(context, 102, callIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.button_call, callPI)
        views.setInt(R.id.icon_call, "setColorFilter", iconTint)

        // Commitment Intent
        val commitIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(MainTabsFragment.EXTRA_OPEN_TAB, 3)
        }
        val commitPI = PendingIntent.getActivity(context, 103, commitIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.button_commitment, commitPI)
        views.setInt(R.id.icon_commitment, "setColorFilter", iconTint)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
