package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.example.myapplication.databinding.FragmentFirstBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CheckinFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedPreferences: SharedPreferences
    private val selectedCalendar = Calendar.getInstance().apply { clearTime() }
    private val dateFormatter = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
    private lateinit var db: AppDatabase

    private var currentCheckIns: List<CheckIn> = emptyList()
    private var currentSchedules: List<CallSchedule> = emptyList()
    private var calendarAdapter: CalendarAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreferences = requireActivity().getSharedPreferences("prefs", Context.MODE_PRIVATE)
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
        binding.calendarRecyclerView.layoutManager = GridLayoutManager(requireContext(), 7)
        refreshCalendarGrid()
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.checkInDao().getAllCheckIns().combine(db.callScheduleDao().getAllSchedules()) { checkIns, schedules ->
                    checkIns to schedules
                }.collect { (checkIns, schedules) ->
                    currentCheckIns = checkIns
                    currentSchedules = schedules
                    refreshCalendarGrid()
                    loadCheckInForSelectedDate()
                }
            }
        }
    }

    private fun refreshCalendarGrid() {
        val days = mutableListOf<com.example.myapplication.CalendarDay>()
        
        // Find start of 4-week window
        val startDayOfWeek = sharedPreferences.getInt("start_day_of_week", Calendar.SUNDAY)
        val cal = Calendar.getInstance()
        cal.clearTime()
        
        // Move to start of current week
        while (cal.get(Calendar.DAY_OF_WEEK) != startDayOfWeek) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        // Move back 3 more weeks to have 4 weeks total
        cal.add(Calendar.DAY_OF_YEAR, -21)

        val today = Calendar.getInstance().apply { clearTime() }
        val dayCheckInMap = currentCheckIns.associateBy { it.date.trim() }
        val scheduleMap = currentSchedules.associateBy { it.dayOfWeek.trim().lowercase() }

        for (i in 0 until 28) {
            val dateStr = dateFormatter.format(cal.time)
            val dayName = SimpleDateFormat("EEEE", Locale.US).format(cal.time).lowercase()
            val checkIn = dayCheckInMap[dateStr]
            val schedule = scheduleMap[dayName]
            
            days.add(com.example.myapplication.CalendarDay(
                dayOfMonth = cal.get(Calendar.DAY_OF_MONTH).toString(),
                dateString = dateStr,
                scaleOption = checkIn?.scaleOption,
                isSelected = isSameDay(cal, selectedCalendar),
                isToday = isSameDay(cal, today),
                hasCall = schedule != null,
                isInbound = schedule?.isInbound ?: false
            ))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        calendarAdapter = CalendarAdapter(days) { clickedDay ->
            clickedDay.dateString?.let {
                try {
                    dateFormatter.parse(it)?.let { date ->
                        selectedCalendar.time = date
                        selectedCalendar.clearTime()
                        refreshCalendarGrid()
                        loadCheckInForSelectedDate()
                    }
                } catch (e: Exception) {}
            }
        }
        binding.calendarRecyclerView.adapter = calendarAdapter
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
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
                
                if (checkIn.callMade) {
                    binding.textviewSummaryCall.visibility = View.VISIBLE
                    val completedIds = checkIn.completedScheduleIds.split(",").filter { it.isNotBlank() }
                    if (completedIds.size > 1) {
                        binding.textviewSummaryCall.text = getString(R.string.label_calls_made_count, completedIds.size)
                    } else {
                        binding.textviewSummaryCall.setText(R.string.label_call_made_simple)
                    }
                    
                    val iconRes = if (checkIn.isInboundCall) R.drawable.ic_call_inbound else R.drawable.ic_call_outbound
                    val icon = ContextCompat.getDrawable(requireContext(), iconRes)?.apply {
                        val size = (binding.textviewSummaryCall.textSize * 1.1f).toInt()
                        setBounds(0, 0, size, size)
                        setTint(ContextCompat.getColor(requireContext(), R.color.purple_500))
                    }
                    binding.textviewSummaryCall.setCompoundDrawables(icon, null, null, null)
                    binding.textviewSummaryCall.compoundDrawablePadding = 8
                } else {
                    binding.textviewSummaryCall.visibility = View.GONE
                    binding.textviewSummaryCall.setCompoundDrawables(null, null, null, null)
                }
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
