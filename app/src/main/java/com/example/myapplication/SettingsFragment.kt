package com.fasterscale.app

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
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
import com.fasterscale.app.databinding.DialogAddContactBinding
import com.fasterscale.app.databinding.FragmentSettingsBinding
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

    private val requestNotificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        updatePermissionStatus()
        if (isGranted) {
            Toast.makeText(requireContext(), "Notifications enabled", Toast.LENGTH_SHORT).show()
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

        setupPermissionSection()
        setupNotificationSettings()
        setupStartDaySettings()
        setupShareSettings()
        setupDemoMode()
        setupContactSettings()
        setupBackupRestore()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun setupPermissionSection() {
        binding.buttonFixNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
        }

        binding.buttonFixAlarms.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
        }

        binding.buttonFixBattery.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback to general settings
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        if (_binding == null) return
        val context = context ?: return
        
        // Notifications
        val notificationsGranted = AlarmHelper.isNotificationPermissionGranted(context)
        binding.textStatusNotifications.text = "Status: ${if (notificationsGranted) "Allowed" else "Blocked"}"
        binding.buttonFixNotifications.visibility = if (notificationsGranted) View.GONE else View.VISIBLE
        binding.iconPermissionNotifications.setImageResource(
            if (notificationsGranted) android.R.drawable.presence_online else android.R.drawable.presence_busy
        )

        // Exact Alarms
        val alarmsGranted = AlarmHelper.canScheduleExact(context)
        binding.textStatusAlarms.text = "Status: ${if (alarmsGranted) "Allowed" else "Restricted"}"
        binding.buttonFixAlarms.visibility = if (alarmsGranted) View.GONE else View.VISIBLE
        binding.iconPermissionAlarms.setImageResource(
            if (alarmsGranted) android.R.drawable.presence_online else android.R.drawable.presence_busy
        )

        // Battery Optimization
        val batteryIgnored = AlarmHelper.isBatteryOptimizationIgnored(context)
        binding.textStatusBattery.text = "Status: ${if (batteryIgnored) "Not Optimized (Good)" else "Optimized (May delay alerts)"}"
        binding.buttonFixBattery.visibility = if (batteryIgnored) View.GONE else View.VISIBLE
        binding.iconPermissionBattery.setImageResource(
            if (batteryIgnored) android.R.drawable.presence_online else android.R.drawable.presence_busy
        )
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
                val h = sharedPreferences.getInt("reminder_hour", 8)
                val m = sharedPreferences.getInt("reminder_minute", 0)
                AlarmHelper.scheduleDailyReminder(requireContext(), h, m)
            } else {
                AlarmHelper.cancelDailyReminder(requireContext())
            }
        }

        binding.buttonChangeTime.setOnClickListener {
            val hCurrent = sharedPreferences.getInt("reminder_hour", 8)
            val mCurrent = sharedPreferences.getInt("reminder_minute", 0)
            TimePickerDialog(requireContext(), { _, h, m ->
                sharedPreferences.edit()
                    .putInt("reminder_hour", h)
                    .putInt("reminder_minute", m)
                    .apply()
                updateTimeText(h, m)
                if (binding.switchReminder.isChecked) {
                    AlarmHelper.scheduleDailyReminder(requireContext(), h, m)
                }
            }, hCurrent, mCurrent, false).show()
        }

        // Phone Call Reminder
        val isPhoneCallReminderEnabled = sharedPreferences.getBoolean("phone_call_reminder_enabled", false)
        binding.switchPhoneCallReminder.isChecked = isPhoneCallReminderEnabled
        binding.switchPhoneCallReminder.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("phone_call_reminder_enabled", isChecked).apply()
            if (isChecked) {
                rescheduleAllCallAlarms()
                Toast.makeText(requireContext(), "Phone call reminders enabled", Toast.LENGTH_SHORT).show()
            } else {
                cancelAllCallAlarms()
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
                putExtra("isInbound", false)
            }
            requireContext().sendBroadcast(intent)
            Toast.makeText(requireContext(), "Test call alert sent", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rescheduleAllCallAlarms() {
        viewLifecycleOwner.lifecycleScope.launch {
            val schedules = db.callScheduleDao().getAllSchedules().first()
            schedules.forEach { schedule ->
                AlarmHelper.scheduleCallAlarm(requireContext(), schedule)
            }
        }
    }

    private fun cancelAllCallAlarms() {
        viewLifecycleOwner.lifecycleScope.launch {
            val schedules = db.callScheduleDao().getAllSchedules().first()
            schedules.forEach { schedule ->
                AlarmHelper.cancelCallAlarm(requireContext(), schedule.id)
            }
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

    private fun setupDemoMode() {
        val isDemoMode = sharedPreferences.getBoolean("demo_mode", false)
        binding.switchDemoMode.isChecked = isDemoMode
        binding.switchDemoMode.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("demo_mode", isChecked).apply()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Restart Required")
                .setMessage(R.string.demo_mode_desc)
                .setPositiveButton("OK", null)
                .show()
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
            showAddContactDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            db.contactDao().getAllContacts().collect { contacts ->
                contactAdapter.submitList(contacts)
            }
        }
    }

    private fun showAddContactDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Contact")
            .setItems(arrayOf("Pick from Contacts", "Enter Manually")) { _, which ->
                if (which == 0) {
                    checkPermissionAndPickContact()
                } else {
                    showManualContactDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showManualContactDialog() {
        val dialogBinding = DialogAddContactBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("New Contact")
            .setView(dialogBinding.root)
            .setPositiveButton("Add") { _, _ ->
                val name = dialogBinding.edittextContactName.text.toString()
                val phone = dialogBinding.edittextContactPhone.text.toString()
                if (name.isNotBlank() && phone.isNotBlank()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        db.contactDao().insertContact(Contact(name = name, phoneNumber = phone))
                    }
                } else {
                    Toast.makeText(requireContext(), "Name and phone are required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                            is Double -> {
                                // GSON deserializes all numbers as Double by default
                                if (value % 1.0 == 0.0) {
                                    editor.putInt(key, value.toInt())
                                } else {
                                    editor.putFloat(key, value.toFloat())
                                }
                            }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
