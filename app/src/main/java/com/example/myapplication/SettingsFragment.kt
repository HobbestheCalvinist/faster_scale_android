package com.example.myapplication

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.myapplication.databinding.FragmentSettingsBinding
import java.util.*

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreferences = requireActivity().getSharedPreferences("prefs", Context.MODE_PRIVATE)

        val isReminderEnabled = sharedPreferences.getBoolean("reminder_enabled", false)
        val hour = sharedPreferences.getInt("reminder_hour", 8)
        val minute = sharedPreferences.getInt("reminder_minute", 0)

        // Set initial state without triggering listener
        binding.switchReminder.isChecked = isReminderEnabled
        updateTimeText(hour, minute)

        binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("reminder_enabled", isChecked).apply()
            if (isChecked) {
                scheduleReminder(hour, minute)
            } else {
                cancelReminder()
            }
        }

        binding.buttonChangeTime.setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, m ->
                sharedPreferences.edit()
                    .putInt("reminder_hour", h)
                    .putInt("reminder_minute", m)
                    .apply()
                updateTimeText(h, m)
                if (binding.switchReminder.isChecked) {
                    scheduleReminder(h, m)
                }
            }, hour, minute, false).show()
        }
    }

    private fun updateTimeText(hour: Int, minute: Int) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        val amPm = if (calendar.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        val displayHour = if (hour % 12 == 0) 12 else hour % 12
        binding.textviewSelectedTime.text = String.format("%02d:%02d %s", displayHour, minute, amPm)
    }

    private fun scheduleReminder(hour: Int, minute: Int) {
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                // Fallback to inexact alarm if permission is missing to prevent crash
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    private fun cancelReminder() {
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager.cancel(pendingIntent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
