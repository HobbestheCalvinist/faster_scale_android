package com.example.myapplication

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.applandeo.materialcalendarview.CalendarDay
import com.applandeo.materialcalendarview.EventDay
import com.applandeo.materialcalendarview.listeners.OnDayClickListener
import com.example.myapplication.databinding.FragmentFirstBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CheckinFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private val selectedCalendar = Calendar.getInstance().apply { clearTime() }
    private val dateFormatter = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
    private lateinit var db: AppDatabase

    private var currentCheckIns: List<CheckIn> = emptyList()
    private var currentSchedules: List<CallSchedule> = emptyList()

    private val scaleOptions by lazy {
        listOf(
            getString(R.string.scale_restoration) to getString(R.string.desc_restoration),
            getString(R.string.scale_forgetting) to getString(R.string.desc_forgetting),
            getString(R.string.scale_anxiety) to getString(R.string.desc_anxiety),
            getString(R.string.scale_speeding) to getString(R.string.desc_speeding),
            getString(R.string.scale_ticked_off) to getString(R.string.desc_ticked_off),
            getString(R.string.scale_exhausted) to getString(R.string.desc_exhausted),
            getString(R.string.scale_relapse) to getString(R.string.desc_relapse)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())

        arguments?.getString("selectedDate")?.let { dateStr ->
            try {
                dateFormatter.parse(dateStr.trim())?.let { date ->
                    selectedCalendar.time = date
                    selectedCalendar.clearTime()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        setupCalendar()
        setupDropdown()
        updateDateDisplay()
        loadCheckInForSelectedDate()
        observeData()

        binding.buttonSave.setOnClickListener {
            saveCheckIn()
        }

        binding.buttonEditCheckin.setOnClickListener {
            showEditMode(true)
        }

        binding.textinputlayoutDescription.setStartIconOnClickListener {
            binding.edittextDescription.setText("")
            binding.chipgroupBehaviors.children.forEach { view ->
                if (view is Chip) view.isChecked = false
            }
        }
    }

    private fun Calendar.clearTime() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun setupCalendar() {
        binding.calendarView.setOnDayClickListener(object : OnDayClickListener {
            override fun onDayClick(eventDay: EventDay) {
                selectedCalendar.time = eventDay.calendar.time
                selectedCalendar.clearTime()
                updateDateDisplay()
                loadCheckInForSelectedDate()
            }
        })
        
        try {
            binding.calendarView.setDate(selectedCalendar)
        } catch (e: Exception) {}
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Combine both data sources to ensure the calendar refreshes when either changes
                db.checkInDao().getAllCheckIns().combine(db.callScheduleDao().getAllSchedules()) { checkIns, schedules ->
                    checkIns to schedules
                }.collect { (checkIns, schedules) ->
                    currentCheckIns = checkIns
                    currentSchedules = schedules
                    refreshCalendarMarkers()
                }
            }
        }
    }

    private fun refreshCalendarMarkers() {
        val calendarDays = mutableListOf<CalendarDay>()
        
        // Map check-ins by unique date key for robust matching
        val dayCheckInMap = mutableMapOf<String, CheckIn>()
        currentCheckIns.forEach { checkIn ->
            try {
                dateFormatter.parse(checkIn.date.trim())?.let { date ->
                    val c = Calendar.getInstance().apply { 
                        time = date
                        clearTime()
                    }
                    dayCheckInMap[dateToKey(c)] = checkIn
                }
            } catch (e: Exception) {}
        }

        val callDaysOfWeek = currentSchedules.map { it.dayOfWeek.trim() }.toSet()

        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -3)
        cal.clearTime()
        val endCal = Calendar.getInstance()
        endCal.add(Calendar.MONTH, 12) // Show forward for a year
        endCal.clearTime()

        while (cal.before(endCal)) {
            val dayKey = dateToKey(cal)
            val dayName = SimpleDateFormat("EEEE", Locale.US).format(cal.time)

            val checkIn = dayCheckInMap[dayKey]
            val hasCall = callDaysOfWeek.contains(dayName)

            if (checkIn != null || hasCall) {
                val day = CalendarDay(cal.clone() as Calendar)
                
                // Combine background color and call icon into one Drawable
                day.backgroundDrawable = getCalendarDayDrawable(checkIn?.scaleOption, hasCall)
                
                if (checkIn != null) {
                    // Fix: setLabelColor expects a color resource ID in many versions of this library
                    day.labelColor = R.color.white
                }
                
                calendarDays.add(day)
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        binding.calendarView.setCalendarDays(calendarDays)
    }

    private fun dateToKey(cal: Calendar): String {
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
    }

    private fun getCalendarDayDrawable(scaleOption: String?, hasCall: Boolean): Drawable? {
        val layers = mutableListOf<Drawable>()
        
        // Layer 0: Check-in background circle
        if (scaleOption != null) {
            layers.add(getCircleDrawable(scaleOption))
        }
        
        // Layer 1: Phone icon
        if (hasCall) {
            val callIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_call)?.mutate()
            callIcon?.let {
                val tint = if (scaleOption != null) Color.WHITE 
                           else getThemeColor(com.google.android.material.R.attr.colorPrimary)
                DrawableCompat.setTint(it, tint)
                layers.add(it)
            }
        }
        
        if (layers.isEmpty()) return null
        
        val layered = LayerDrawable(layers.toTypedArray())
        
        // Positioning logic for layered icons
        if (scaleOption != null && hasCall) {
            // Icon is smaller and in the top-right corner if there is a background
            val iconSize = dpToPx(12)
            layered.setLayerGravity(1, Gravity.TOP or Gravity.END)
            layered.setLayerSize(1, iconSize, iconSize)
            layered.setLayerInset(1, 0, dpToPx(2), dpToPx(2), 0)
        } else if (hasCall) {
            // Icon is centered and larger if there is no background
            val iconSize = dpToPx(18)
            layered.setLayerSize(0, iconSize, iconSize)
            layered.setLayerGravity(0, Gravity.CENTER)
        }
        
        return layered
    }

    private fun getCircleDrawable(scaleOption: String): Drawable {
        val shape = GradientDrawable()
        shape.shape = GradientDrawable.OVAL
        shape.setColor(getScaleColor(scaleOption))
        return shape
    }

    private fun getScaleColor(scaleOption: String): Int {
        val trimmedScale = scaleOption.trim().lowercase()
        val colorRes = when {
            trimmedScale.contains("restoration") -> R.color.color_restoration
            trimmedScale.contains("forgetting") -> R.color.color_forgetting
            trimmedScale.contains("anxiety") -> R.color.color_anxiety
            trimmedScale.contains("speeding") -> R.color.color_speeding
            trimmedScale.contains("ticked") -> R.color.color_ticked_off
            trimmedScale.contains("exhausted") -> R.color.color_exhausted
            trimmedScale.contains("relapse") -> R.color.color_relapse
            else -> R.color.purple_500
        }
        return ContextCompat.getColor(requireContext(), colorRes)
    }

    private fun getThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun setupDropdown() {
        val displayOptions = scaleOptions.map { "${it.first}: ${it.second}" }
        val adapter = ArrayAdapter(requireContext(), R.layout.item_dropdown_multiline, displayOptions)
        binding.autocompletetextviewScale.setAdapter(adapter)

        binding.autocompletetextviewScale.setOnItemClickListener { _, _, position, _ ->
            val selectedText = adapter.getItem(position) ?: ""
            val selectedOption = scaleOptions.find { "${it.first}: ${it.second}" == selectedText }?.first ?: ""
            if (selectedOption.isNotEmpty()) {
                updateBehaviorsSection(selectedOption)
            }
        }

        binding.textinputlayoutScale.setStartIconOnClickListener {
            binding.autocompletetextviewScale.setText(null, false)
            adapter.filter.filter(null)
            binding.layoutBehaviorsSection.visibility = View.GONE
            binding.autocompletetextviewScale.clearFocus()
        }
    }

    private fun updateBehaviorsSection(scaleOption: String) {
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
            val behaviors = resources.getStringArray(behaviorsResId)
            behaviors.forEach { behavior ->
                val chip = Chip(requireContext()).apply {
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

    private fun updateDateDisplay() {
        val dateStr = dateFormatter.format(selectedCalendar.time)
        binding.textviewViewDate.text = dateStr
    }

    private fun loadCheckInForSelectedDate() {
        val date = dateFormatter.format(selectedCalendar.time)
        viewLifecycleOwner.lifecycleScope.launch {
            val checkIn = db.checkInDao().getCheckInByDate(date.trim())
            val adapter = binding.autocompletetextviewScale.adapter as? ArrayAdapter<*>

            if (checkIn != null) {
                binding.textviewViewScale.text = checkIn.scaleOption
                binding.textviewViewDescription.text = checkIn.description
                binding.textviewViewDescription.visibility = if (checkIn.description.isEmpty()) View.GONE else View.VISIBLE
                
                binding.textviewViewScale.setTextColor(getScaleColor(checkIn.scaleOption))

                binding.edittextDescription.setText(checkIn.description)
                val displayValue = scaleOptions.find { it.first == checkIn.scaleOption.trim() }?.let { "${it.first}: ${it.second}" }
                if (displayValue != null) {
                    binding.autocompletetextviewScale.setText(displayValue, false)
                    adapter?.filter?.filter(null)
                    updateBehaviorsSection(checkIn.scaleOption)
                }
                
                showEditMode(false)
            } else {
                binding.autocompletetextviewScale.setText(null, false)
                adapter?.filter?.filter(null)
                binding.edittextDescription.setText("")
                binding.layoutBehaviorsSection.visibility = View.GONE
                showEditMode(true)
            }
        }
    }

    private fun showEditMode(isEdit: Boolean) {
        if (isEdit) {
            binding.layoutEditMode.visibility = View.VISIBLE
            binding.layoutViewMode.visibility = View.GONE
        } else {
            binding.layoutEditMode.visibility = View.GONE
            binding.layoutViewMode.visibility = View.VISIBLE
        }
    }

    private fun saveCheckIn() {
        val selectedText = binding.autocompletetextviewScale.text.toString()
        if (selectedText.isBlank()) {
            Toast.makeText(requireContext(), "Please select a scale option", Toast.LENGTH_SHORT).show()
            return
        }

        val scaleOption = scaleOptions.find { "${it.first}: ${it.second}" == selectedText }?.first ?: selectedText
        val description = binding.edittextDescription.text.toString()
        val date = dateFormatter.format(selectedCalendar.time)

        viewLifecycleOwner.lifecycleScope.launch {
            val existingCheckIn = db.checkInDao().getCheckInByDate(date.trim())
            val checkIn = if (existingCheckIn != null) {
                existingCheckIn.copy(scaleOption = scaleOption, description = description)
            } else {
                CheckIn(date = date, scaleOption = scaleOption, description = description)
            }

            db.checkInDao().insertCheckIn(checkIn)
            Toast.makeText(requireContext(), "Check-in saved!", Toast.LENGTH_SHORT).show()
            loadCheckInForSelectedDate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
