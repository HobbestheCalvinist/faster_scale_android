package com.example.myapplication

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.myapplication.databinding.FragmentFirstBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class CheckinFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private val selectedCalendar = Calendar.getInstance()
    private val currentMonthCalendar = Calendar.getInstance()
    
    private val dateFormatter = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
    private val monthYearFormatter = SimpleDateFormat("MMMM yyyy", Locale.US)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check for passed date argument from History
        arguments?.getString("selectedDate")?.let { dateStr ->
            try {
                dateFormatter.parse(dateStr)?.let { date ->
                    selectedCalendar.time = date
                    currentMonthCalendar.time = date
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        setupCalendar()
        updateDateDisplay()
        loadCheckInForSelectedDate()

        binding.buttonPickDate.setOnClickListener {
            showDatePicker()
        }

        binding.buttonSave.setOnClickListener {
            saveCheckIn()
        }

        binding.buttonToHistory.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_HistoryFragment)
        }

        binding.buttonPrevMonth.setOnClickListener {
            currentMonthCalendar.add(Calendar.MONTH, -1)
            refreshProgressCalendar()
        }

        binding.buttonNextMonth.setOnClickListener {
            currentMonthCalendar.add(Calendar.MONTH, 1)
            refreshProgressCalendar()
        }

        binding.radiogroupScale.setOnCheckedChangeListener { group, checkedId ->
            val radioButton = group.findViewById<RadioButton>(checkedId)
            if (radioButton != null) {
                updateBehaviorsSection(radioButton.text.toString())
            }
        }
    }

    private fun updateBehaviorsSection(scaleOption: String) {
        binding.layoutBehaviorsSection.visibility = View.VISIBLE
        binding.chipgroupBehaviors.removeAllViews()

        val (descResId, behaviorsResId) = when (scaleOption) {
            getString(R.string.scale_restoration) -> Pair(R.string.desc_restoration, R.array.behaviors_restoration)
            getString(R.string.scale_forgetting) -> Pair(R.string.desc_forgetting, R.array.behaviors_forgetting)
            getString(R.string.scale_anxiety) -> Pair(R.string.desc_anxiety, R.array.behaviors_anxiety)
            getString(R.string.scale_speeding) -> Pair(R.string.desc_speeding, R.array.behaviors_speeding)
            getString(R.string.scale_ticked_off) -> Pair(R.string.desc_ticked_off, R.array.behaviors_ticked_off)
            getString(R.string.scale_exhausted) -> Pair(R.string.desc_exhausted, R.array.behaviors_exhausted)
            getString(R.string.scale_relapse) -> Pair(R.string.desc_relapse, R.array.behaviors_relapse)
            else -> Pair(null, null)
        }

        if (descResId != null) {
            binding.textviewScaleDescription.text = getString(descResId)
        }

        if (behaviorsResId != null) {
            val behaviors = resources.getStringArray(behaviorsResId)
            behaviors.forEach { behavior ->
                val chip = Chip(requireContext()).apply {
                    text = behavior
                    isCheckable = false
                    setOnClickListener {
                        val currentText = binding.edittextDescription.text.toString()
                        val behaviorText = "• $behavior"
                        if (currentText.isEmpty()) {
                            binding.edittextDescription.setText(behaviorText)
                        } else if (!currentText.contains(behavior)) {
                            if (!currentText.endsWith("\n")) {
                                binding.edittextDescription.append("\n")
                            }
                            binding.edittextDescription.append(behaviorText)
                        }
                        binding.edittextDescription.setSelection(binding.edittextDescription.text?.length ?: 0)
                    }
                }
                binding.chipgroupBehaviors.addView(chip)
            }
        }
    }

    private fun setupCalendar() {
        binding.recyclerviewCalendar.layoutManager = GridLayoutManager(requireContext(), 7)
        refreshProgressCalendar()
    }

    private fun refreshProgressCalendar() {
        binding.textviewCalendarMonth.text = monthYearFormatter.format(currentMonthCalendar.time)
        
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val allCheckIns = db.checkInDao().getAllCheckIns().first()
            val dateToScaleMap = allCheckIns.associate { it.date.trim() to it.scaleOption }

            val days = mutableListOf<CalendarDay>()
            
            val today = Calendar.getInstance()
            val todayString = dateFormatter.format(today.time)
            val selectedString = dateFormatter.format(selectedCalendar.time)

            val cal = currentMonthCalendar.clone() as Calendar
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

            for (i in 0 until firstDayOfWeek) {
                days.add(CalendarDay("", null))
            }

            for (i in 1..daysInMonth) {
                cal.set(Calendar.DAY_OF_MONTH, i)
                val dateString = dateFormatter.format(cal.time)
                days.add(
                    CalendarDay(
                        dayOfMonth = i.toString(),
                        dateString = dateString,
                        scaleOption = dateToScaleMap[dateString],
                        isSelected = dateString == selectedString,
                        isToday = dateString == todayString
                    )
                )
            }

            binding.recyclerviewCalendar.adapter = CalendarAdapter(days) { day ->
                if (day.dateString != null) {
                    val clickedDate = dateFormatter.parse(day.dateString)
                    if (clickedDate != null) {
                        selectedCalendar.time = clickedDate
                        updateDateDisplay()
                        loadCheckInForSelectedDate()
                        refreshProgressCalendar()
                    }
                }
            }
        }
    }

    private fun showDatePicker() {
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                selectedCalendar.set(Calendar.YEAR, year)
                selectedCalendar.set(Calendar.MONTH, month)
                selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                updateDateDisplay()
                loadCheckInForSelectedDate()
                refreshProgressCalendar()
            },
            selectedCalendar.get(Calendar.YEAR),
            selectedCalendar.get(Calendar.MONTH),
            selectedCalendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun updateDateDisplay() {
        binding.textviewSelectedDate.text = dateFormatter.format(selectedCalendar.time)
    }

    private fun loadCheckInForSelectedDate() {
        val date = binding.textviewSelectedDate.text.toString()
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val checkIn = db.checkInDao().getCheckInByDate(date.trim())
            
            if (checkIn != null) {
                binding.edittextDescription.setText(checkIn.description)
                // Select appropriate radio button
                var found = false
                for (i in 0 until binding.radiogroupScale.childCount) {
                    val view = binding.radiogroupScale.getChildAt(i)
                    if (view is RadioButton) {
                        if (view.text.toString() == checkIn.scaleOption) {
                            view.isChecked = true
                            updateBehaviorsSection(checkIn.scaleOption)
                            found = true
                            break
                        }
                    }
                }
                if (!found) {
                    binding.radiogroupScale.clearCheck()
                    binding.layoutBehaviorsSection.visibility = View.GONE
                }
            } else {
                binding.radiogroupScale.clearCheck()
                binding.edittextDescription.setText("")
                binding.layoutBehaviorsSection.visibility = View.GONE
            }
        }
    }

    private fun saveCheckIn() {
        val selectedId = binding.radiogroupScale.checkedRadioButtonId
        if (selectedId == -1) {
            Toast.makeText(requireContext(), "Please select a scale option", Toast.LENGTH_SHORT).show()
            return
        }

        val radioButton = binding.root.findViewById<RadioButton>(selectedId)
        val scaleOption = radioButton.text.toString()
        val description = binding.edittextDescription.text.toString()
        val date = binding.textviewSelectedDate.text.toString()

        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            // Check if a record already exists for this date to preserve the ID
            val existingCheckIn = db.checkInDao().getCheckInByDate(date.trim())
            
            val checkIn = if (existingCheckIn != null) {
                existingCheckIn.copy(scaleOption = scaleOption, description = description)
            } else {
                CheckIn(date = date, scaleOption = scaleOption, description = description)
            }

            db.checkInDao().insertCheckIn(checkIn)
            Toast.makeText(requireContext(), "Check-in saved!", Toast.LENGTH_SHORT).show()
            refreshProgressCalendar()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
