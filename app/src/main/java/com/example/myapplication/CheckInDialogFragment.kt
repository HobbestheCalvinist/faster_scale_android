package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.databinding.DialogCheckInBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CheckInDialogFragment : DialogFragment() {

    private var _binding: DialogCheckInBinding? = null
    private val binding get() = _binding!!

    private var db: AppDatabase? = null
    private val dateFormatter = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
    private var selectedDate: Calendar = Calendar.getInstance()

    private var scaleOptions: List<Pair<String, String>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_MyApplication_Dialog)
        
        val dateMillis = arguments?.getLong(ARG_DATE) ?: System.currentTimeMillis()
        selectedDate.timeInMillis = dateMillis
        selectedDate.set(Calendar.HOUR_OF_DAY, 0)
        selectedDate.set(Calendar.MINUTE, 0)
        selectedDate.set(Calendar.SECOND, 0)
        selectedDate.set(Calendar.MILLISECOND, 0)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogCheckInBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val context = context ?: return
        db = AppDatabase.getDatabase(context)

        scaleOptions = listOf(
            getString(R.string.scale_restoration) to getString(R.string.desc_restoration),
            getString(R.string.scale_forgetting) to getString(R.string.desc_forgetting),
            getString(R.string.scale_anxiety) to getString(R.string.desc_anxiety),
            getString(R.string.scale_speeding) to getString(R.string.desc_speeding),
            getString(R.string.scale_ticked_off) to getString(R.string.desc_ticked_off),
            getString(R.string.scale_exhausted) to getString(R.string.desc_exhausted),
            getString(R.string.scale_relapse) to getString(R.string.desc_relapse)
        )

        binding.textviewDialogTitle.text = getString(R.string.check_in_title_format, dateFormatter.format(selectedDate.time))

        setupDropdown()
        checkScheduledCall()
        loadExistingCheckIn()

        binding.buttonSave.setOnClickListener {
            saveCheckIn()
        }

        binding.buttonCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun setupDropdown() {
        val context = context ?: return
        val displayOptions = scaleOptions.map { "${it.first}: ${it.second}" }
        val adapter = ArrayAdapter(context, R.layout.item_dropdown_multiline, displayOptions)
        binding.autocompletetextviewScale.setAdapter(adapter)

        binding.autocompletetextviewScale.setOnItemClickListener { _, _, position, _ ->
            val selectedText = adapter.getItem(position) ?: ""
            val selectedOption = scaleOptions.find { "${it.first}: ${it.second}" == selectedText }?.first ?: ""
            if (selectedOption.isNotEmpty()) {
                updateBehaviorsSection(selectedOption)
            }
        }
    }

    private fun updateBehaviorsSection(scaleOption: String) {
        val context = context ?: return
        binding.layoutBehaviorsSection.visibility = View.VISIBLE
        binding.chipgroupBehaviors.removeAllViews()

        val behaviorsResId = when {
            scaleOption.trim().contains(getString(R.string.scale_restoration)) -> R.array.behaviors_restoration
            scaleOption.trim().contains(getString(R.string.scale_forgetting)) -> R.array.behaviors_forgetting
            scaleOption.trim().contains(getString(R.string.scale_anxiety)) -> R.array.behaviors_anxiety
            scaleOption.trim().contains(getString(R.string.scale_speeding)) -> R.array.behaviors_speeding
            scaleOption.trim().contains(getString(R.string.scale_ticked_off)) -> R.array.behaviors_ticked_off
            scaleOption.trim().contains(getString(R.string.scale_exhausted)) -> R.array.behaviors_exhausted
            scaleOption.trim().contains(getString(R.string.scale_relapse)) -> R.array.behaviors_relapse
            else -> null
        }

        if (behaviorsResId != null) {
            val behaviors = try {
                resources.getStringArray(behaviorsResId)
            } catch (e: Exception) {
                emptyArray<String>()
            }
            behaviors.forEach { behavior ->
                val chip = Chip(context).apply {
                    text = behavior
                    isCheckable = true
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
                        isChecked = true
                    }
                }
                binding.chipgroupBehaviors.addView(chip)
            }
        }
    }

    private fun checkScheduledCall() {
        val currentDb = db ?: return
        val dayOfWeek = SimpleDateFormat("EEEE", Locale.US).format(selectedDate.time)
        val shortDate = SimpleDateFormat("MMM dd", Locale.US).format(selectedDate.time)
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val schedules = currentDb.callScheduleDao().getAllSchedules().firstOrNull() ?: emptyList()
                val scheduleForToday = schedules.find { it.dayOfWeek.trim().equals(dayOfWeek, ignoreCase = true) }
                
                _binding?.let { b ->
                    if (scheduleForToday != null) {
                        b.cardCallInfo.visibility = View.VISIBLE
                        b.textviewScheduledCallDetails.text = getString(R.string.scheduled_call_details_format, shortDate, scheduleForToday.time)
                        b.textviewScheduledContact.text = getString(R.string.scheduled_contact_format, scheduleForToday.contactName)
                        b.textviewScheduledPhone.text = getString(R.string.scheduled_phone_format, scheduleForToday.contactPhone)
                        
                        b.buttonCallNow.setOnClickListener {
                            try {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${scheduleForToday.contactPhone}")
                                }
                                startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open dialer", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        b.cardCallInfo.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                _binding?.cardCallInfo?.visibility = View.GONE
            }
        }
    }

    private fun loadExistingCheckIn() {
        val currentDb = db ?: return
        val dateKey = dateFormatter.format(selectedDate.time)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val checkIn = currentDb.checkInDao().getCheckInByDate(dateKey.trim())
                _binding?.let { b ->
                    if (checkIn != null) {
                        val displayValue = scaleOptions.find { it.first == checkIn.scaleOption.trim() }?.let { "${it.first}: ${it.second}" }
                        if (displayValue != null) {
                            b.autocompletetextviewScale.setText(displayValue, false)
                            updateBehaviorsSection(checkIn.scaleOption)
                        }
                        b.edittextDescription.setText(checkIn.description)
                        b.switchCallMade.isChecked = checkIn.callMade
                    }
                }
            } catch (e: Exception) {
                // Silently fail or log
            }
        }
    }

    private fun saveCheckIn() {
        val currentDb = db ?: return
        val selectedText = binding.autocompletetextviewScale.text.toString()
        if (selectedText.isBlank()) {
            Toast.makeText(requireContext(), "Please select a scale option", Toast.LENGTH_SHORT).show()
            return
        }

        val scaleOption = scaleOptions.find { "${it.first}: ${it.second}" == selectedText }?.first ?: selectedText
        val description = binding.edittextDescription.text.toString()
        val callMade = if (binding.cardCallInfo.visibility == View.VISIBLE) binding.switchCallMade.isChecked else false
        val date = dateFormatter.format(selectedDate.time)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val existingCheckIn = currentDb.checkInDao().getCheckInByDate(date.trim())
                val checkIn = if (existingCheckIn != null) {
                    existingCheckIn.copy(scaleOption = scaleOption, description = description, callMade = callMade)
                } else {
                    CheckIn(date = date, scaleOption = scaleOption, description = description, callMade = callMade)
                }

                currentDb.checkInDao().insertCheckIn(checkIn)
                context?.let {
                    Toast.makeText(it, "Check-in saved!", Toast.LENGTH_SHORT).show()
                }
                dismiss()
            } catch (e: Exception) {
                context?.let {
                    Toast.makeText(it, "Error saving check-in", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_DATE = "arg_date"

        fun newInstance(dateMillis: Long): CheckInDialogFragment {
            return CheckInDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_DATE, dateMillis)
                }
            }
        }
    }
}
