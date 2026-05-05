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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.databinding.FragmentHistoryBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A simple [Fragment] subclass as the third destination in the navigation.
 */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var db: AppDatabase

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.checkInDao().getAllCheckIns().collect { checkIns ->
                    val shareTrustedOnly = sharedPreferences.getBoolean("share_trusted_only", false)
                    binding.recyclerviewHistory.adapter = HistoryAdapter(
                        checkIns = checkIns,
                        isTrustedOnly = shareTrustedOnly,
                        onEditClick = { checkIn ->
                            val bundle = Bundle().apply {
                                putString("selectedDate", checkIn.date)
                            }
                            findNavController().navigate(R.id.action_HistoryFragment_to_FirstFragment, bundle)
                        },
                        onDeleteClick = { checkIn ->
                            showDeleteConfirmation(checkIn)
                        },
                        onShareClick = { checkIn ->
                            handleShareAction(checkIn)
                        }
                    )

                    binding.buttonShareAll.setOnClickListener {
                        handleShareAllAction(checkIns)
                    }
                }
            }
        }
    }

    private fun handleShareAction(checkIn: CheckIn) {
        val shareTrustedOnly = sharedPreferences.getBoolean("share_trusted_only", false)
        if (shareTrustedOnly) {
            shareWithTrustedContacts(getShareText(checkIn))
        } else {
            shareGeneric(getShareText(checkIn))
        }
    }

    private fun handleShareAllAction(checkIns: List<CheckIn>) {
        if (checkIns.isEmpty()) return
        val shareTrustedOnly = sharedPreferences.getBoolean("share_trusted_only", false)
        val report = getHistoryReport(checkIns)
        if (shareTrustedOnly) {
            shareWithTrustedContacts(report)
        } else {
            shareGeneric(report, "Share History Report")
        }
    }

    private fun getShareText(checkIn: CheckIn): String {
        return """
            Faster Scale Check-in
            Date: ${checkIn.date}
            Level: ${checkIn.scaleOption}
            Notes: ${checkIn.description}
        """.trimIndent()
    }

    private fun getHistoryReport(checkIns: List<CheckIn>): String {
        val report = StringBuilder("Faster Scale Recovery - Full History\n\n")
        checkIns.forEach { checkIn ->
            report.append("Date: ${checkIn.date}\n")
            report.append("Level: ${checkIn.scaleOption}\n")
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
    }
}
