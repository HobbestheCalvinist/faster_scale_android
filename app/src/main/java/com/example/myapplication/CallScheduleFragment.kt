package com.example.myapplication

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.databinding.DialogAddEditCallScheduleBinding
import com.example.myapplication.databinding.FragmentCallScheduleBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*

class CallScheduleFragment : Fragment() {

    private var _binding: FragmentCallScheduleBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private lateinit var adapter: CallScheduleAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCallScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())

        setupRecyclerView()

        binding.fabAddCallSchedule.setOnClickListener {
            showAddEditDialog(null)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            db.callScheduleDao().getAllSchedules().collect { schedules ->
                adapter.submitList(schedules)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = CallScheduleAdapter(
            onEditClick = { schedule -> showAddEditDialog(schedule) },
            onDeleteClick = { schedule -> showDeleteConfirmation(schedule) },
            onCallClick = { schedule -> makeCall(schedule.contactPhone) }
        )
        binding.recyclerviewCallSchedule.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CallScheduleFragment.adapter
        }
    }

    private fun showAddEditDialog(schedule: CallSchedule?) {
        val dialogBinding = DialogAddEditCallScheduleBinding.inflate(layoutInflater)
        val isEdit = schedule != null

        // Setup Days of the week Spinner (AutoCompleteTextView)
        val days = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val dayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, days)
        (dialogBinding.exposedDropdownDay.editText as? AutoCompleteTextView)?.setAdapter(dayAdapter)

        // Setup Contact Spinner
        viewLifecycleOwner.lifecycleScope.launch {
            val contacts = db.contactDao().getAllContacts().first()
            if (contacts.isEmpty()) {
                Toast.makeText(requireContext(), R.string.no_contacts_available, Toast.LENGTH_LONG).show()
                return@launch
            }

            val contactNames = contacts.map { it.name }
            val contactAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, contactNames)
            (dialogBinding.exposedDropdownContact.editText as? AutoCompleteTextView)?.setAdapter(contactAdapter)

            // Pre-fill if editing
            schedule?.let { s ->
                (dialogBinding.exposedDropdownDay.editText as? AutoCompleteTextView)?.setText(s.dayOfWeek, false)
                dialogBinding.textviewTimeValue.text = s.time
                (dialogBinding.exposedDropdownContact.editText as? AutoCompleteTextView)?.setText(s.contactName, false)
            }
        }

        dialogBinding.buttonSetTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)

            TimePickerDialog(requireContext(), { _, h, m ->
                val amPm = if (h < 12) "AM" else "PM"
                val hDisplay = if (h % 12 == 0) 12 else h % 12
                val timeString = String.format(Locale.getDefault(), "%02d:%02d %s", hDisplay, m, amPm)
                dialogBinding.textviewTimeValue.text = timeString
            }, hour, minute, false).show()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isEdit) R.string.title_edit_call_schedule else R.string.title_add_call_schedule)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val day = dialogBinding.exposedDropdownDay.editText?.text.toString()
                val time = dialogBinding.textviewTimeValue.text.toString()
                val contactName = dialogBinding.exposedDropdownContact.editText?.text.toString()

                if (day.isNotEmpty() && time.isNotEmpty() && contactName.isNotEmpty()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val contacts = db.contactDao().getAllContacts().first()
                        val selectedContact = contacts.find { it.name == contactName }
                        if (selectedContact != null) {
                            val newSchedule = CallSchedule(
                                id = schedule?.id ?: 0,
                                dayOfWeek = day,
                                time = time,
                                contactId = selectedContact.id,
                                contactName = selectedContact.name,
                                contactPhone = selectedContact.phoneNumber
                            )
                            if (isEdit) {
                                db.callScheduleDao().updateSchedule(newSchedule)
                            } else {
                                db.callScheduleDao().insertSchedule(newSchedule)
                            }
                        }
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showDeleteConfirmation(schedule: CallSchedule) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.menu_delete)
            .setMessage(R.string.delete_call_schedule_confirmation)
            .setPositiveButton(R.string.menu_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    db.callScheduleDao().deleteSchedule(schedule)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun makeCall(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
