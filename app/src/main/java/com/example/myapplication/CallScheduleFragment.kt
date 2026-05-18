package com.example.myapplication

import android.app.TimePickerDialog
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
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
                val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                
                val sortedSchedules = schedules.sortedWith(compareBy<CallSchedule> {
                    val dayInt = getDayInt(it.dayOfWeek)
                    // Calculate days until this call (0 = today, 1 = tomorrow, etc.)
                    (dayInt - today + 7) % 7
                }.thenBy {
                    val timeStr = it.time.uppercase()
                    val isPm = timeStr.endsWith("PM")
                    val timePart = timeStr.replace("AM", "").replace("PM", "").trim()
                    val parts = timePart.split(":")
                    if (parts.size >= 2) {
                        var hour = parts[0].toIntOrNull() ?: 0
                        val minute = parts[1].toIntOrNull() ?: 0
                        if (isPm && hour != 12) hour += 12
                        if (!isPm && hour == 12) hour = 0
                        hour * 60 + minute
                    } else {
                        0
                    }
                })

                val listItems = mutableListOf<CallScheduleListItem>()
                val todaySchedules = sortedSchedules.filter { getDayInt(it.dayOfWeek) == today }
                val upcomingSchedules = sortedSchedules.filter { getDayInt(it.dayOfWeek) != today }

                if (todaySchedules.isNotEmpty()) {
                    listItems.add(CallScheduleListItem.Header("Today"))
                    listItems.addAll(todaySchedules.map { CallScheduleListItem.Item(it) })
                }

                if (upcomingSchedules.isNotEmpty()) {
                    listItems.add(CallScheduleListItem.Header("Upcoming"))
                    listItems.addAll(upcomingSchedules.map { CallScheduleListItem.Item(it) })
                }

                adapter.submitList(listItems)
            }
        }
    }

    private fun getDayInt(day: String): Int {
        return when (day.trim()) {
            "Sunday" -> Calendar.SUNDAY
            "Monday" -> Calendar.MONDAY
            "Tuesday" -> Calendar.TUESDAY
            "Wednesday" -> Calendar.WEDNESDAY
            "Thursday" -> Calendar.THURSDAY
            "Friday" -> Calendar.FRIDAY
            "Saturday" -> Calendar.SATURDAY
            else -> Calendar.SUNDAY
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

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isEdit) R.string.title_edit_call_schedule else R.string.title_add_call_schedule)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val day = dialogBinding.exposedDropdownDay.editText?.text.toString()
                val time = dialogBinding.textviewTimeValue.text.toString()
                val contactName = dialogBinding.exposedDropdownContact.editText?.text.toString()
                val isInbound = dialogBinding.switchInbound.isChecked

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
                                contactPhone = selectedContact.phoneNumber,
                                isInbound = isInbound
                            )
                            if (isEdit) {
                                db.callScheduleDao().updateSchedule(newSchedule)
                                AlarmHelper.scheduleCallAlarm(requireContext(), newSchedule)
                            } else {
                                val id = db.callScheduleDao().insertSchedule(newSchedule)
                                AlarmHelper.scheduleCallAlarm(requireContext(), newSchedule.copy(id = id.toInt()))
                            }
                        }
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()

        val saveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        saveButton.isEnabled = false

        fun validate() {
            val day = dialogBinding.exposedDropdownDay.editText?.text.toString()
            val time = dialogBinding.textviewTimeValue.text.toString()
            val contactName = dialogBinding.exposedDropdownContact.editText?.text.toString()
            val notSet = getString(R.string.time_not_set)
            
            saveButton.isEnabled = day.isNotBlank() && 
                                   time.isNotBlank() && 
                                   time != notSet && 
                                   contactName.isNotBlank()
        }

        dialogBinding.exposedDropdownDay.editText?.addTextChangedListener { validate() }
        dialogBinding.exposedDropdownContact.editText?.addTextChangedListener { validate() }

        // Setup Contact Spinner
        viewLifecycleOwner.lifecycleScope.launch {
            val contacts = db.contactDao().getAllContacts().first()
            if (contacts.isEmpty()) {
                Toast.makeText(requireContext(), R.string.no_contacts_available, Toast.LENGTH_LONG).show()
                dialog.dismiss()
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
                dialogBinding.switchInbound.isChecked = s.isInbound
            }
            validate()
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
                validate()
            }, hour, minute, false).show()
        }
    }

    private fun showDeleteConfirmation(schedule: CallSchedule) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.menu_delete)
            .setMessage(R.string.delete_call_schedule_confirmation)
            .setPositiveButton(R.string.menu_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    AlarmHelper.cancelCallAlarm(requireContext(), schedule.id)
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
