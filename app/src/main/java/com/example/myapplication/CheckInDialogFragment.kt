package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    private var scaleOptionsList: List<ScaleOption> = emptyList()

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

        scaleOptionsList = listOf(
            ScaleOption(getString(R.string.scale_restoration), getString(R.string.desc_restoration)),
            ScaleOption(getString(R.string.scale_forgetting), getString(R.string.desc_forgetting)),
            ScaleOption(getString(R.string.scale_anxiety), getString(R.string.desc_anxiety)),
            ScaleOption(getString(R.string.scale_speeding), getString(R.string.desc_speeding)),
            ScaleOption(getString(R.string.scale_ticked_off), getString(R.string.desc_ticked_off)),
            ScaleOption(getString(R.string.scale_exhausted), getString(R.string.desc_exhausted)),
            ScaleOption(getString(R.string.scale_relapse), getString(R.string.desc_relapse))
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
        val adapter = ScaleOptionAdapter(context, scaleOptionsList)
        binding.autocompletetextviewScale.setAdapter(adapter)

        binding.autocompletetextviewScale.setOnItemClickListener { _, _, position, _ ->
            val selectedOption = adapter.getItem(position)
            if (selectedOption != null) {
                updateBehaviorsSection(selectedOption.title)
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
            
            val currentDescription = binding.edittextDescription.text.toString()
            val currentLines = currentDescription.lines().map { 
                it.trim().removePrefix("•").removePrefix("\u2022").trim() 
            }
            
            behaviors.forEach { behavior ->
                val chip = Chip(context).apply {
                    text = behavior
                    isCheckable = true
                    // Set initial state based on description
                    isChecked = currentLines.contains(behavior.trim())
                    
                    setOnClickListener {
                        val isNowChecked = isChecked
                        val currentText = binding.edittextDescription.text.toString()
                        val behaviorWithBullet = "• $behavior"
                        
                        if (isNowChecked) {
                            val lines = currentText.lines().map { it.trim().removePrefix("•").removePrefix("\u2022").trim() }
                            if (!lines.contains(behavior.trim())) {
                                if (currentText.isBlank()) {
                                    binding.edittextDescription.setText(behaviorWithBullet)
                                } else {
                                    val separator = if (currentText.endsWith("\n")) "" else "\n"
                                    binding.edittextDescription.append(separator + behaviorWithBullet)
                                }
                            }
                        } else {
                            val lines = currentText.lines().filter { 
                                it.trim().removePrefix("•").removePrefix("\u2022").trim() != behavior.trim() 
                            }
                            binding.edittextDescription.setText(lines.filter { it.isNotBlank() }.joinToString("\n"))
                        }
                        binding.edittextDescription.setSelection(binding.edittextDescription.text?.length ?: 0)
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
                        val displayOption = scaleOptionsList.find { it.title == checkIn.scaleOption.trim() }
                        if (displayOption != null) {
                            b.autocompletetextviewScale.setText(displayOption.title, false)
                            // Important: Update behaviors AFTER setting the description to ensure chips are correctly checked
                            b.edittextDescription.setText(checkIn.description)
                            updateBehaviorsSection(checkIn.scaleOption)
                        }
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

        val scaleOption = scaleOptionsList.find { it.title == selectedText }?.title ?: selectedText
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
