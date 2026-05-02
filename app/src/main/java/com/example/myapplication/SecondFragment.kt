package com.example.myapplication

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.myapplication.databinding.FragmentSecondBinding
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DayViewDecorator
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private val binding get() = _binding!!

    private val dateFormatter = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
    private val utcDateFormatter = SimpleDateFormat("MMMM dd, yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    private var checkInDates = setOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonSecond.setOnClickListener {
            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
        }

        // Keep local check-in dates updated
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            db.checkInDao().getAllCheckIns().collect { list ->
                checkInDates = list.map { it.date.trim() }.toSet()
            }
        }

        // Setup standard CalendarView
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance()
            calendar.set(year, month, dayOfMonth)
            loadDetailForDate(dateFormatter.format(calendar.time))
        }

        // Button to open the marked Material calendar
        binding.buttonOpenMarkedCalendar.setOnClickListener {
            showMarkedCalendar()
        }

        // Load detail for today by default
        loadDetailForDate(dateFormatter.format(Date()))
    }

    private fun showMarkedCalendar() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            // Fetch all check-ins and ensure we have the latest dates
            val latestDates = db.checkInDao().getAllCheckIns().first().map { it.date.trim() }.toSet()

            val constraintsBuilder = CalendarConstraints.Builder()
            
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .setCalendarConstraints(constraintsBuilder.build())
                .setDayViewDecorator(CheckInDayViewDecorator(latestDates))
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                // MaterialDatePicker selection is in UTC
                val selectedDate = utcDateFormatter.format(Date(selection))
                loadDetailForDate(selectedDate)
                
                // Sync the standard CalendarView
                binding.calendarView.date = selection
            }

            datePicker.show(childFragmentManager, "MATERIAL_DATE_PICKER")
        }
    }

    private fun loadDetailForDate(date: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val checkIn = db.checkInDao().getCheckInByDate(date.trim())

            if (checkIn != null) {
                binding.cardDayDetail.visibility = View.VISIBLE
                binding.textviewNoData.visibility = View.GONE
                
                binding.textviewDetailDate.text = checkIn.date
                binding.textviewDetailScale.text = checkIn.scaleOption
                binding.textviewDetailDescription.text = checkIn.description
                binding.textviewDetailDescription.visibility = 
                    if (checkIn.description.isNotEmpty()) View.VISIBLE else View.GONE
            } else {
                binding.cardDayDetail.visibility = View.GONE
                binding.textviewNoData.visibility = View.VISIBLE
                binding.textviewNoData.text = getString(R.string.no_checkin_for_date, date)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class CheckInDayViewDecorator(
        private val checkInDates: Set<String>
    ) : DayViewDecorator() {

        // Use a static-like formatter to ensure consistency
        private val internalFormatter = SimpleDateFormat("MMMM dd, yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        private fun isDateMarked(year: Int, month: Int, day: Int): Boolean {
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.clear()
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, day)
            val dateString = internalFormatter.format(calendar.time)
            return checkInDates.contains(dateString)
        }

        override fun getBackgroundColor(
            context: Context,
            year: Int,
            month: Int,
            day: Int,
            valid: Boolean,
            selected: Boolean
        ): ColorStateList? {
            return if (isDateMarked(year, month, day)) {
                // Use a more visible highlight color (teal_200)
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.teal_200))
            } else {
                null
            }
        }

        override fun getCompoundDrawableBottom(
            context: Context,
            year: Int,
            month: Int,
            day: Int,
            valid: Boolean,
            selected: Boolean
        ): Drawable? {
            return if (isDateMarked(year, month, day)) {
                ContextCompat.getDrawable(context, R.drawable.ic_dot_marker)
            } else {
                null
            }
        }

        // Also add to top for better visibility if bottom is cut off
        override fun getCompoundDrawableTop(
            context: Context,
            year: Int,
            month: Int,
            day: Int,
            valid: Boolean,
            selected: Boolean
        ): Drawable? {
            return if (isDateMarked(year, month, day)) {
                ContextCompat.getDrawable(context, R.drawable.ic_dot_marker)
            } else {
                null
            }
        }

        constructor(parcel: Parcel) : this(
            parcel.createStringArrayList()?.toSet() ?: emptySet()
        )

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeStringList(checkInDates.toList())
        }

        companion object CREATOR : Parcelable.Creator<CheckInDayViewDecorator> {
            override fun createFromParcel(parcel: Parcel): CheckInDayViewDecorator {
                return CheckInDayViewDecorator(parcel)
            }

            override fun newArray(size: Int): Array<CheckInDayViewDecorator?> {
                return arrayOfNulls(size)
            }
        }
    }
}
