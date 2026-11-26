package com.fasterscale.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.fasterscale.app.databinding.FragmentAboutBinding
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private lateinit var sharedPreferences: SharedPreferences

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
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())
        sharedPreferences = requireActivity().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        
        setupAboutSection()
        setupBackupRestore()
    }

    private fun setupAboutSection() {
        try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            val version = pInfo.versionName
            val lastUpdateTime = pInfo.lastUpdateTime
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val dateString = sdf.format(Date(lastUpdateTime))
            binding.textviewVersion.text = "Version $version (Build: $dateString)"
        } catch (e: Exception) {
            binding.textviewVersion.text = "Version 1.0"
        }

        binding.buttonGenesisWebsite.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.genesisprocess.org/"))
            startActivity(intent)
        }

        binding.buttonEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:HobbestheCalvinist@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "App Feedback")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "No email app found", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonCoffee.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/hobbescalvinist"))
            startActivity(intent)
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
                val checkIns = db.checkInDao().getAllCheckIns().first().map {
                    CheckInDTO(it.id, it.date, it.scaleOption, it.feeling, it.feelingEmoji, it.feelingIntensity, it.description, it.callMade, it.isInboundCall, it.completedScheduleIds, it.liedToday, it.workingCommitments, it.temptationLevel)
                }
                val contacts = db.contactDao().getAllContacts().first().map {
                    ContactDTO(it.id, it.name, it.phoneNumber)
                }
                val schedules = db.callScheduleDao().getAllSchedules().first().map {
                    CallScheduleDTO(it.id, it.dayOfWeek, it.time, it.contactId, it.contactName, it.contactPhone, it.isInbound)
                }
                val commitments = (db.commitmentDao().getActiveCommitments().first() + 
                                   db.commitmentDao().getPendingCommitments().first() + 
                                   db.commitmentDao().getCompletedCommitments().first()).map {
                    CommitmentDTO(
                        id = it.id,
                        title = it.title,
                        description = it.description,
                        frequency = it.frequency,
                        interval = it.interval,
                        daysOfWeekBitmask = it.daysOfWeekBitmask,
                        dayOfMonth = it.dayOfMonth,
                        targetCompletions = it.targetCompletions,
                        completedCount = it.completedCount,
                        totalCompletions = it.totalCompletions,
                        lastCompletionTimestamp = it.lastCompletionTimestamp,
                        timestamp = it.timestamp,
                        startDate = it.startDate,
                        endDate = it.endDate,
                        status = it.status
                    )
                }
                val customFeelings = db.customFeelingDao().getAllCustomFeelings().first().map {
                    CustomFeelingDTO(it.id, emoji = it.emoji, label = it.label, levelId = it.levelId)
                }
                val prefs = sharedPreferences.all

                val backupData = BackupData(checkIns, contacts, schedules, commitments, customFeelings, prefs)
                val json = Gson().toJson(backupData)

                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(json.toByteArray())
                    }
                }
                Toast.makeText(requireContext(), "Backup exported successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
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
                    // Restore Database with null-safety and sanitization for backward compatibility
                    backupData.checkIns?.forEach { dto ->
                        db.checkInDao().insertCheckIn(CheckIn(
                            id = dto.id,
                            date = dto.date ?: "",
                            scaleOption = dto.scaleOption ?: "",
                            feeling = dto.feeling ?: "",
                            feelingEmoji = dto.feelingEmoji ?: "",
                            feelingIntensity = dto.feelingIntensity ?: 0,
                            description = dto.description ?: "",
                            callMade = dto.callMade ?: false,
                            isInboundCall = dto.isInboundCall ?: false,
                            completedScheduleIds = dto.completedScheduleIds ?: "",
                            liedToday = dto.liedToday ?: false,
                            workingCommitments = dto.workingCommitments ?: false,
                            temptationLevel = dto.temptationLevel ?: 0
                        )) 
                    }
                    backupData.contacts?.forEach { dto ->
                        db.contactDao().insertContact(Contact(
                            id = dto.id,
                            name = dto.name ?: "",
                            phoneNumber = dto.phoneNumber ?: ""
                        )) 
                    }
                    backupData.callSchedules?.forEach { dto ->
                        db.callScheduleDao().insertSchedule(CallSchedule(
                            id = dto.id,
                            dayOfWeek = dto.dayOfWeek ?: "",
                            time = dto.time ?: "",
                            contactId = dto.contactId ?: 0,
                            contactName = dto.contactName ?: "",
                            contactPhone = dto.contactPhone ?: "" ,
                            isInbound = dto.isInbound ?: false
                        )) 
                    }
                    backupData.commitments?.forEach { dto ->
                        // Migrate legacy fields to new status system
                        val initialStatus = if (dto.status != null) {
                            dto.status
                        } else if (dto.isCompleted == true) {
                            2 // Completed Forever
                        } else {
                            0 // Active
                        }

                        db.commitmentDao().insertCommitment(Commitment(
                            id = dto.id,
                            title = dto.title ?: "",
                            description = dto.description ?: "",
                            frequency = dto.frequency ?: "WEEKLY",
                            interval = dto.interval ?: 1,
                            daysOfWeekBitmask = dto.daysOfWeekBitmask ?: 0,
                            dayOfMonth = dto.dayOfMonth ?: 1,
                            targetCompletions = dto.targetCompletions ?: 1,
                            completedCount = dto.completedCount ?: 0,
                            totalCompletions = dto.totalCompletions ?: 0,
                            lastCompletionTimestamp = dto.lastCompletionTimestamp ?: 0,
                            timestamp = dto.timestamp ?: System.currentTimeMillis(),
                            startDate = dto.startDate ?: System.currentTimeMillis(),
                            endDate = dto.endDate,
                            status = initialStatus
                        )) 
                    }
                    backupData.customFeelings?.forEach { dto ->
                        db.customFeelingDao().insertCustomFeeling(CustomFeeling(
                            id = dto.id,
                            emoji = dto.emoji ?: "",
                            label = dto.label ?: dto.meaning ?: "",
                            levelId = dto.levelId ?: "restoration" // Fallback for old feelings
                        )) 
                    }

                    // Restore Preferences
                    val editor = sharedPreferences.edit()
                    backupData.preferences?.forEach { (key, value) ->
                        when (value) {
                            is Boolean -> editor.putBoolean(key, value)
                            is Double -> {
                                // GSON deserializes all numbers as Double by default.
                                val longValue = value.toLong()
                                if (value == longValue.toDouble()) {
                                    if (longValue in Int.MIN_VALUE..Int.MAX_VALUE) {
                                        editor.putInt(key, longValue.toInt())
                                    } else {
                                        editor.putLong(key, longValue)
                                    }
                                } else {
                                    editor.putFloat(key, value.toFloat())
                                }
                            }
                            is String -> editor.putString(key, value)
                        }
                    }
                    editor.apply()
                }

                Toast.makeText(requireContext(), "Import completed! Please restart the app for all changes to take effect.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
