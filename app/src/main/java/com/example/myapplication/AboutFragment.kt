package com.example.myapplication

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
import com.example.myapplication.databinding.FragmentAboutBinding
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
