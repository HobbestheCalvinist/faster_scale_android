package com.example.myapplication

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.myapplication.databinding.FragmentSecondBinding
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

        // Set up calendar listener
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance()
            calendar.set(year, month, dayOfMonth)
            val selectedDate = dateFormatter.format(calendar.time)
            loadDetailForDate(selectedDate)
        }

        // Load detail for today by default
        loadDetailForDate(dateFormatter.format(Date()))
    }

    private fun loadDetailForDate(date: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val checkIn = db.checkInDao().getCheckInByDate(date)

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
}