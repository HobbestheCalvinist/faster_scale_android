package com.example.myapplication

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.databinding.FragmentSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var db: AppDatabase
    private lateinit var contactAdapter: ContactAdapter

    private val daysOfWeek = listOf(
        "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    )

    private val contactPickerLauncher = registerForActivityResult(ActivityResultContracts.PickContact()) { uri: Uri? ->
        uri?.let { processSelectedContact(it) }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            contactPickerLauncher.launch(null)
        } else {
            Toast.makeText(requireContext(), "Permission denied to read contacts", Toast.LENGTH_SHORT).show()
        }
    }

    private val createBackupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let { performBackup(it) }
    }

    private val restoreBackupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { performRestore(it) }
    }

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
        db = AppDatabase.getDatabase(requireContext())

        setupNotificationSettings()
        setupStartDaySettings()
        setupShareSettings()
        setupContactSettings()
        setupBackupRestore()
    }

    private fun setupNotificationSettings() {
        // Daily Check-in Reminder
        val isReminderEnabled = sharedPreferences.getBoolean("reminder_enabled", false)
        val hour = sharedPreferences.getInt("reminder_hour", 8)
        val minute = sharedPreferences.getInt("reminder_minute", 0)

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

        // Phone Call Reminder
        val isPhoneCallReminderEnabled = sharedPreferences.getBoolean("phone_call_reminder_enabled", false)
        binding.switchPhoneCallReminder.isChecked = isPhoneCallReminderEnabled
        binding.switchPhoneCallReminder.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("phone_call_reminder_enabled", isChecked).apply()
            if (isChecked) {
                Toast.makeText(requireContext(), "Phone call reminders enabled", Toast.LENGTH_SHORT).show()
            }
        }

        // Test Notifications
        binding.buttonTestNotification.setOnClickListener {
            val intent = Intent(requireContext(), ReminderReceiver::class.java)
            requireContext().sendBroadcast(intent)
            Toast.makeText(requireContext(), "Test notification sent", Toast.LENGTH_SHORT).show()
        }

        binding.buttonTestCallNotification.setOnClickListener {
            val intent = Intent(requireContext(), CallAlarmReceiver::class.java).apply {
                putExtra("contactName", "Test Contact")
                putExtra("contactPhone", "555-0199")
                putExtra("scheduleId", 999)
            }
            requireContext().sendBroadcast(intent)
            Toast.makeText(requireContext(), "Test call alert sent", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupStartDaySettings() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, daysOfWeek)
        binding.autocompletetextviewStartDay.setAdapter(adapter)

        val currentStartDay = sharedPreferences.getInt("start_day_of_week", Calendar.SUNDAY)
        val currentDayName = when (currentStartDay) {
            Calendar.SUNDAY -> "Sunday"
            Calendar.MONDAY -> "Monday"
            Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"
            Calendar.SATURDAY -> "Saturday"
            else -> "Sunday"
        }
        binding.autocompletetextviewStartDay.setText(currentDayName, false)

        binding.autocompletetextviewStartDay.setOnItemClickListener { _, _, position, _ ->
            val selectedDay = when (daysOfWeek[position]) {
                "Sunday" -> Calendar.SUNDAY
                "Monday" -> Calendar.MONDAY
                "Tuesday" -> Calendar.TUESDAY
                "Wednesday" -> Calendar.WEDNESDAY
                "Thursday" -> Calendar.THURSDAY
                "Friday" -> Calendar.FRIDAY
                "Saturday" -> Calendar.SATURDAY
                else -> Calendar.SUNDAY
            }
            sharedPreferences.edit().putInt("start_day_of_week", selectedDay).apply()
        }
    }

    private fun setupShareSettings() {
        val isShareTrustedEnabled = sharedPreferences.getBoolean("share_trusted_only", false)
        binding.switchShareTrusted.isChecked = isShareTrustedEnabled
        binding.switchShareTrusted.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("share_trusted_only", isChecked).apply()
        }
    }

    private fun setupContactSettings() {
        // Collapsible Section
        binding.layoutContactsHeader.setOnClickListener {
            val isVisible = binding.layoutContactsContent.visibility == View.VISIBLE
            binding.layoutContactsContent.visibility = if (isVisible) View.GONE else View.VISIBLE
            binding.imageviewContactsExpand.setImageResource(
                if (isVisible) android.R.drawable.arrow_down_float else android.R.drawable.arrow_up_float
            )
        }

        contactAdapter = ContactAdapter { contact ->
            showDeleteContactConfirmation(contact)
        }

        binding.recyclerviewContacts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = contactAdapter
        }

        binding.buttonAddContact.setOnClickListener {
            checkPermissionAndPickContact()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            db.contactDao().getAllContacts().collect { contacts ->
                contactAdapter.submitList(contacts)
            }
        }
    }

    private fun setupBackupRestore() {
        binding.buttonBackup.setOnClickListener {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            createBackupLauncher.launch("faster_scale_backup_$timeStamp.json")
        }

        binding.buttonRestore.setOnClickListener {
            restoreBackupLauncher.launch(arrayOf("application/json"))
        }
    }

    private fun performBackup(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val checkIns = db.checkInDao().getAllCheckIns().first()
                val contacts = db.contactDao().getAllContacts().first()
                val schedules = db.callScheduleDao().getAllSchedules().first()
                val commitments = db.commitmentDao().getActiveCommitments().first() + db.commitmentDao().getCompletedCommitments().first()
                val prefs = sharedPreferences.all

                val backupData = BackupData(checkIns, contacts, schedules, commitments, prefs)
                val json = Gson().toJson(backupData)

                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(json.toByteArray())
                    }
                }
                Toast.makeText(requireContext(), "Backup created successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun performRestore(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).readText()
                    } ?: ""
                }

                val backupData = Gson().fromJson(json, BackupData::class.java)

                withContext(Dispatchers.IO) {
                    // Restore Database
                    backupData.checkIns.forEach { db.checkInDao().insertCheckIn(it) }
                    backupData.contacts.forEach { db.contactDao().insertContact(it) }
                    backupData.callSchedules.forEach { db.callScheduleDao().insertSchedule(it) }
                    backupData.commitments.forEach { db.commitmentDao().insertCommitment(it) }

                    // Restore Preferences
                    val editor = sharedPreferences.edit()
                    backupData.preferences.forEach { (key, value) ->
                        when (value) {
                            is Boolean -> editor.putBoolean(key, value)
                            is Int -> editor.putInt(key, value)
                            is Long -> editor.putLong(key, value)
                            is Float -> editor.putFloat(key, value)
                            is String -> editor.putString(key, value)
                        }
                    }
                    editor.apply()
                }

                Toast.makeText(requireContext(), "Restore completed! Please restart the app for all changes to take effect.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkPermissionAndPickContact() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED -> {
                contactPickerLauncher.launch(null)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    private fun processSelectedContact(contactUri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val contact = getContactDetails(contactUri)
            if (contact != null) {
                db.contactDao().insertContact(contact)
            } else {
                Toast.makeText(requireContext(), "Could not retrieve contact details or phone number", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun getContactDetails(contactUri: Uri): Contact? = withContext(Dispatchers.IO) {
        var name: String? = null
        var phoneNumber: String? = null
        val contentResolver = requireContext().contentResolver

        // Get Name
        contentResolver.query(contactUri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                if (nameIndex != -1) name = cursor.getString(nameIndex)
                
                val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                if (idIndex != -1) {
                    val contactId = cursor.getString(idIndex)
                    
                    // Get Phone Number
                    contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId),
                        null
                    )?.use { phoneCursor ->
                        if (phoneCursor.moveToFirst()) {
                            val phoneIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            if (phoneIndex != -1) phoneNumber = phoneCursor.getString(phoneIndex)
                        }
                    }
                }
            }
        }

        if (name != null && phoneNumber != null) {
            Contact(name = name!!, phoneNumber = phoneNumber!!)
        } else null
    }

    private fun showDeleteContactConfirmation(contact: Contact) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Contact")
            .setMessage("Are you sure you want to delete ${contact.name}?")
            .setPositiveButton("Delete") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    db.contactDao().deleteContact(contact)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
