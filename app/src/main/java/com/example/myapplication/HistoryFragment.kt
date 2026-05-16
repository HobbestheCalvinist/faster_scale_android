package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.databinding.FragmentHistoryBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * A simple [Fragment] subclass as the third destination in the navigation.
 */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var db: AppDatabase
    private val dateFormatter = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
    private val monthYearFormatter = SimpleDateFormat("MMMM yyyy", Locale.US)
    private var historyAdapter: HistoryAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedPreferences = requireActivity().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        db = AppDatabase.getDatabase(requireContext())

        binding.recyclerviewHistory.layoutManager = LinearLayoutManager(context)

        binding.buttonHistoryCheckIn.setOnClickListener {
            showCheckInDialog(System.currentTimeMillis())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.checkInDao().getAllCheckIns().collect { checkIns ->
                    val shareTrustedOnly = sharedPreferences.getBoolean("share_trusted_only", false)
                    val groupedItems = groupCheckIns(checkIns)
                    
                    if (historyAdapter == null) {
                        historyAdapter = HistoryAdapter(
                            allItems = groupedItems,
                            isTrustedOnly = shareTrustedOnly,
                            onEditClick = { checkIn ->
                                try {
                                    dateFormatter.parse(checkIn.date)?.let { date ->
                                        showCheckInDialog(date.time)
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(requireContext(), "Error parsing date", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDeleteClick = { checkIn ->
                                showDeleteConfirmation(checkIn)
                            },
                            onShareClick = { checkIn ->
                                handleShareAction(checkIn)
                            }
                        )
                        
                        // Collapse all groups except "This Week" by default
                        val olderGroups = groupedItems
                            .filterIsInstance<HistoryListItem.Header>()
                            .map { it.title }
                            .filter { it != "This Week" }
                        historyAdapter?.collapseGroups(olderGroups)
                        
                        binding.recyclerviewHistory.adapter = historyAdapter
                    } else {
                        historyAdapter?.updateData(groupedItems)
                    }

                    binding.buttonShareAll.setOnClickListener {
                        handleShareWeekAction(checkIns)
                    }
                }
            }
        }
    }

    private fun showCheckInDialog(dateMillis: Long) {
        val dialog = CheckInDialogFragment.newInstance(dateMillis)
        dialog.show(childFragmentManager, "CheckInDialog")
    }

    private fun groupCheckIns(checkIns: List<CheckIn>): List<HistoryListItem> {
        if (checkIns.isEmpty()) return emptyList()

        val sortedCheckIns = checkIns.sortedByDescending { 
            try { dateFormatter.parse(it.date) } catch (e: Exception) { Date(0) }
        }

        val result = mutableListOf<HistoryListItem>()
        val startOfThisWeek = getStartOfThisWeek()

        val thisWeek = mutableListOf<CheckIn>()
        val olderGroups = mutableMapOf<String, MutableList<CheckIn>>()

        sortedCheckIns.forEach { checkIn ->
            val date = try { dateFormatter.parse(checkIn.date) } catch (e: Exception) { null }
            if (date != null) {
                if (!date.before(startOfThisWeek)) {
                    thisWeek.add(checkIn)
                } else {
                    val key = monthYearFormatter.format(date)
                    olderGroups.getOrPut(key) { mutableListOf() }.add(checkIn)
                }
            }
        }

        if (thisWeek.isNotEmpty()) {
            result.add(HistoryListItem.Header("This Week"))
            thisWeek.forEach { result.add(HistoryListItem.Entry(it, "This Week")) }
        }

        val processedMonths = mutableSetOf<String>()
        sortedCheckIns.forEach { checkIn ->
            val date = try { dateFormatter.parse(checkIn.date) } catch (e: Exception) { null }
            if (date != null && date.before(startOfThisWeek)) {
                val key = monthYearFormatter.format(date)
                if (!processedMonths.contains(key)) {
                    result.add(HistoryListItem.Header(key))
                    olderGroups[key]?.forEach { result.add(HistoryListItem.Entry(it, key)) }
                    processedMonths.add(key)
                }
            }
        }

        return result
    }

    private fun getStartOfThisWeek(): Date {
        val startDayOfWeek = sharedPreferences.getInt("start_day_of_week", Calendar.SUNDAY)
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        
        while (cal.get(Calendar.DAY_OF_WEEK) != startDayOfWeek) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return cal.time
    }

    private fun handleShareAction(checkIn: CheckIn) {
        val shareTrustedOnly = sharedPreferences.getBoolean("share_trusted_only", false)
        if (shareTrustedOnly) {
            shareWithTrustedContacts(getShareText(checkIn))
        } else {
            shareGeneric(getShareText(checkIn))
        }
    }

    private fun handleShareWeekAction(checkIns: List<CheckIn>) {
        val startOfThisWeek = getStartOfThisWeek()
        val thisWeekCheckIns = checkIns.filter {
            val date = try { dateFormatter.parse(it.date) } catch (e: Exception) { null }
            date != null && !date.before(startOfThisWeek)
        }.sortedBy { 
            try { dateFormatter.parse(it.date) } catch (e: Exception) { Date(0) }
        }

        if (thisWeekCheckIns.isEmpty()) {
            Toast.makeText(requireContext(), "No check-ins for this week to share.", Toast.LENGTH_SHORT).show()
            return
        }

        val shareTrustedOnly = sharedPreferences.getBoolean("share_trusted_only", false)
        val report = getHistoryReport(thisWeekCheckIns, "This Week's Report")
        if (shareTrustedOnly) {
            shareWithTrustedContacts(report)
        } else {
            shareGeneric(report, "Share Weekly Report")
        }
    }

    private fun getShareText(checkIn: CheckIn): String {
        return """
            |Faster Scale Check-in
            |Date: ${checkIn.date}
            |Level: ${checkIn.scaleOption}
            |Call Made: ${if (checkIn.callMade) "Yes" else "No"}
            |Notes: 
            |${checkIn.description}
        """.trimMargin()
    }

    private fun getHistoryReport(checkIns: List<CheckIn>, title: String): String {
        val report = StringBuilder("Faster Scale Recovery - $title\n\n")
        checkIns.forEach { checkIn ->
            report.append("Date: ${checkIn.date}\n")
            report.append("Level: ${checkIn.scaleOption}\n")
            report.append("Call Made: ${if (checkIn.callMade) "Yes" else "No"}\n")
            if (checkIn.description.isNotBlank()) {
                report.append("Notes: ${checkIn.description}\n")
            }
            report.append("-------------------\n")
        }
        return report.toString()
    }

    private fun shareGeneric(text: String, title: String? = null) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, title)
        startActivity(shareIntent)
    }

    private fun shareWithTrustedContacts(text: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val contacts = db.contactDao().getAllContacts().first()
            if (contacts.isEmpty()) {
                Toast.makeText(requireContext(), "No trusted contacts found. Please add them in Settings.", Toast.LENGTH_LONG).show()
                return@launch
            }

            if (contacts.size == 1) {
                sendSms(contacts[0].phoneNumber, text)
            } else {
                val contactNames = contacts.map { "${it.name} (${it.phoneNumber})" }.toTypedArray()
                AlertDialog.Builder(requireContext())
                    .setTitle("Select Recipient")
                    .setItems(contactNames) { _, which ->
                        sendSms(contacts[which].phoneNumber, text)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun sendSms(phoneNumber: String, text: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phoneNumber")
            putExtra("sms_body", text)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Could not open SMS app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmation(checkIn: CheckIn) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_confirmation_title)
            .setMessage(getString(R.string.delete_confirmation_message, checkIn.date))
            .setPositiveButton(R.string.menu_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    db.checkInDao().deleteCheckIn(checkIn)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        historyAdapter = null
    }
}
