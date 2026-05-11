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
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.applandeo.materialcalendarview.CalendarDay
import com.applandeo.materialcalendarview.EventDay
import com.applandeo.materialcalendarview.listeners.OnDayClickListener
import com.example.myapplication.databinding.FragmentFirstBinding
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

        setupCalendar()
        loadCheckInForSelectedDate()
        observeData()

        binding.buttonCheckIn.setOnClickListener {
            showCheckInDialog(selectedCalendar.timeInMillis)
        }

        binding.buttonEditCheckin.setOnClickListener {
            showCheckInDialog(selectedCalendar.timeInMillis)
        }
    }

    private fun showCheckInDialog(dateMillis: Long) {
        val dialog = CheckInDialogFragment.newInstance(dateMillis)
        dialog.show(childFragmentManager, "CheckInDialog")
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
                db.checkInDao().getAllCheckIns().combine(db.callScheduleDao().getAllSchedules()) { checkIns, schedules ->
                    checkIns to schedules
                }.collect { (checkIns, schedules) ->
                    currentCheckIns = checkIns
                    currentSchedules = schedules
                    refreshCalendarMarkers()
                    loadCheckInForSelectedDate()
                }
            }
        }
    }

    private fun refreshCalendarMarkers() {
        val calendarDays = mutableListOf<CalendarDay>()
        
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
        endCal.add(Calendar.MONTH, 12) 
        endCal.clearTime()

        while (cal.before(endCal)) {
            val dayKey = dateToKey(cal)
            val dayName = SimpleDateFormat("EEEE", Locale.US).format(cal.time)

            val checkIn = dayCheckInMap[dayKey]
            val hasCall = callDaysOfWeek.contains(dayName)

            if (checkIn != null || hasCall) {
                val day = CalendarDay(cal.clone() as Calendar)
                day.backgroundDrawable = getCalendarDayDrawable(checkIn?.scaleOption, hasCall)
                
                if (checkIn != null) {
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
        
        if (scaleOption != null) {
            layers.add(getCircleDrawable(scaleOption))
        }
        
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
        
        if (scaleOption != null && hasCall) {
            val iconSize = dpToPx(12)
            layered.setLayerGravity(1, Gravity.TOP or Gravity.END)
            layered.setLayerSize(1, iconSize, iconSize)
            layered.setLayerInset(1, 0, dpToPx(2), dpToPx(2), 0)
        } else if (hasCall) {
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

    private fun loadCheckInForSelectedDate() {
        val date = dateFormatter.format(selectedCalendar.time)
        viewLifecycleOwner.lifecycleScope.launch {
            val checkIn = db.checkInDao().getCheckInByDate(date.trim())

            if (checkIn != null) {
                binding.cardSummary.visibility = View.VISIBLE
                binding.textviewSummaryDate.text = checkIn.date
                binding.textviewSummaryScale.text = checkIn.scaleOption
                binding.textviewSummaryScale.setTextColor(getScaleColor(checkIn.scaleOption))
                
                binding.textviewSummaryDescription.text = checkIn.description
                binding.textviewSummaryDescription.visibility = if (checkIn.description.isEmpty()) View.GONE else View.VISIBLE
                
                binding.textviewSummaryCall.visibility = if (checkIn.callMade) View.VISIBLE else View.GONE
            } else {
                binding.cardSummary.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
