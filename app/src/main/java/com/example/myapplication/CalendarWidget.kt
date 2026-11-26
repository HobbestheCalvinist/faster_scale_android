package com.fasterscale.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CalendarWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        
        scope.launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    performWidgetUpdate(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Handle manual update request
        if (intent.action == ACTION_MANUAL_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, CalendarWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    private suspend fun performWidgetUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.calendar_widget)

        // Open app when clicking the widget root
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        try {
            val db = AppDatabase.getDatabase(context)
            // Fetch only necessary data
            val checkIns = db.checkInDao().getAllCheckIns().first()
            val schedules = db.callScheduleDao().getAllSchedules().first()
            
            val dateFormatter = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
            val dayNameFormatter = SimpleDateFormat("E", Locale.US)
            
            val cal = Calendar.getInstance()
            val todayDateStr = dateFormatter.format(cal.time)
            
            // Start 6 days ago to end with today on the far right
            cal.add(Calendar.DAY_OF_YEAR, -6)

            val dayNameIds = intArrayOf(R.id.text_day_name_0, R.id.text_day_name_1, R.id.text_day_name_2, R.id.text_day_name_3, R.id.text_day_name_4, R.id.text_day_name_5, R.id.text_day_name_6)
            val dayNumIds = intArrayOf(R.id.text_day_num_0, R.id.text_day_num_1, R.id.text_day_num_2, R.id.text_day_num_3, R.id.text_day_num_4, R.id.text_day_num_5, R.id.text_day_num_6)
            val callImgIds = intArrayOf(R.id.img_call_0, R.id.img_call_1, R.id.img_call_2, R.id.img_call_3, R.id.img_call_4, R.id.img_call_5, R.id.img_call_6)
            val dotImgIds = intArrayOf(R.id.img_dot_0, R.id.img_dot_1, R.id.img_dot_2, R.id.img_dot_3, R.id.img_dot_4, R.id.img_dot_5, R.id.img_dot_6)
            val bgIds = intArrayOf(R.id.img_bg_0, R.id.img_bg_1, R.id.img_bg_2, R.id.img_bg_3, R.id.img_bg_4, R.id.img_bg_5, R.id.img_bg_6)

            val textPrimary = ContextCompat.getColor(context, R.color.widget_text_primary)
            val accent = ContextCompat.getColor(context, R.color.widget_accent)

            for (i in 0 until 7) {
                val dateStr = dateFormatter.format(cal.time)
                val dayName = dayNameFormatter.format(cal.time).first().toString()
                val dayNum = cal.get(Calendar.DAY_OF_MONTH).toString()
                val dayOfWeekFull = SimpleDateFormat("EEEE", Locale.US).format(cal.time).lowercase()

                val checkIn = checkIns.find { it.date.trim() == dateStr }
                val hasCall = schedules.any { it.dayOfWeek.trim().lowercase() == dayOfWeekFull }

                views.setTextViewText(dayNameIds[i], dayName)
                views.setTextViewText(dayNumIds[i], dayNum)
                
                // Reset visibility/colors
                views.setViewVisibility(callImgIds[i], View.GONE)
                views.setViewVisibility(dotImgIds[i], View.GONE)
                views.setInt(bgIds[i], "setColorFilter", Color.TRANSPARENT)
                views.setTextColor(dayNumIds[i], textPrimary)

                if (checkIn != null) {
                    val colorRes = when {
                        checkIn.scaleOption.contains("Restoration", ignoreCase = true) -> R.color.color_restoration
                        checkIn.scaleOption.contains("Forgetting", ignoreCase = true) -> R.color.color_forgetting
                        checkIn.scaleOption.contains("Anxiety", ignoreCase = true) -> R.color.color_anxiety
                        checkIn.scaleOption.contains("Speeding", ignoreCase = true) -> R.color.color_speeding
                        checkIn.scaleOption.contains("Ticked", ignoreCase = true) -> R.color.color_ticked_off
                        checkIn.scaleOption.contains("Exhausted", ignoreCase = true) -> R.color.color_exhausted
                        checkIn.scaleOption.contains("Relapse", ignoreCase = true) -> R.color.color_relapse
                        else -> R.color.purple_500
                    }
                    views.setInt(bgIds[i], "setColorFilter", ContextCompat.getColor(context, colorRes))
                    views.setTextColor(dayNumIds[i], Color.WHITE)
                    views.setViewVisibility(dotImgIds[i], View.VISIBLE)
                    views.setInt(dotImgIds[i], "setColorFilter", Color.WHITE)
                }

                if (hasCall) {
                    views.setViewVisibility(callImgIds[i], View.VISIBLE)
                    val callIconColor = if (checkIn != null) Color.WHITE else accent
                    views.setInt(callImgIds[i], "setColorFilter", callIconColor)
                }

                // Highlight today if no check-in yet
                if (dateStr == todayDateStr && checkIn == null) {
                    views.setTextColor(dayNumIds[i], accent)
                }

                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val ACTION_MANUAL_UPDATE = "com.fasterscale.app.ACTION_WIDGET_UPDATE"

        fun updateAllWidgets(context: Context) {
            val intent = Intent(context.applicationContext, CalendarWidget::class.java).apply {
                action = ACTION_MANUAL_UPDATE
            }
            context.applicationContext.sendBroadcast(intent)
        }
    }
}
