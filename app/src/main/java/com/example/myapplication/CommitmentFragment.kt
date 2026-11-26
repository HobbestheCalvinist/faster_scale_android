package com.fasterscale.app

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.fasterscale.app.databinding.FragmentCommitmentBinding
import com.google.android.material.slider.Slider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CommitmentFragment : Fragment() {

    private var _binding: FragmentCommitmentBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private lateinit var activeAdapter: CommitmentAdapter
    private lateinit var pendingAdapter: CommitmentAdapter
    private lateinit var completedAdapter: CommitmentAdapter
    private var startDayOfWeekPreference: Int = Calendar.SUNDAY

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommitmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())
        
        val prefs = requireActivity().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        startDayOfWeekPreference = prefs.getInt("start_day_of_week", Calendar.SUNDAY)

        val onUpdate = { c: Commitment -> updateCommitment(c) }
        val onEdit = { c: Commitment -> showAddEditCommitmentDialog(c) }
        val onDelete = { c: Commitment -> deleteCommitment(c) }
        val onReset = { c: Commitment -> resetCommitment(c) }
        val onCompleteForever = { c: Commitment -> completeCommitmentForever(c) }

        activeAdapter = CommitmentAdapter(startDayOfWeekPreference, onUpdate, onEdit, onDelete, onReset, onCompleteForever)
        pendingAdapter = CommitmentAdapter(startDayOfWeekPreference, onUpdate, onEdit, onDelete, onReset, onCompleteForever)
        completedAdapter = CommitmentAdapter(startDayOfWeekPreference, onUpdate, onEdit, onDelete, onReset, onCompleteForever)

        binding.recyclerviewActiveCommitments.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = activeAdapter
        }
        binding.recyclerviewPendingCommitments.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = pendingAdapter
        }
        binding.recyclerviewCompletedCommitments.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = completedAdapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                checkAndResetCommitments()
                
                launch {
                    db.commitmentDao().getActiveCommitments().collect { commitments ->
                        activeAdapter.submitList(commitments)
                        binding.textviewActiveHeader.visibility = if (commitments.isEmpty()) View.GONE else View.VISIBLE
                    }
                }
                launch {
                    db.commitmentDao().getPendingCommitments().collect { commitments ->
                        pendingAdapter.submitList(commitments)
                        binding.textviewPendingHeader.visibility = if (commitments.isEmpty()) View.GONE else View.VISIBLE
                    }
                }
                launch {
                    db.commitmentDao().getCompletedCommitments().collect { commitments ->
                        completedAdapter.submitList(commitments)
                        binding.textviewCompletedHeader.visibility = if (commitments.isEmpty()) View.GONE else View.VISIBLE
                    }
                }
            }
        }

        binding.fabAddCommitment.setOnClickListener {
            showAddEditCommitmentDialog()
        }
    }

    private suspend fun checkAndResetCommitments() {
        val allCommitments = db.commitmentDao().getActiveCommitments().first() + 
                             db.commitmentDao().getPendingCommitments().first() +
                             db.commitmentDao().getCompletedCommitments().first()
        val currentTime = System.currentTimeMillis()
        
        allCommitments.forEach { commitment ->
            if (commitment.status == 1) {
                if (commitment.endDate != null && currentTime > commitment.endDate) {
                    db.commitmentDao().updateCommitment(commitment.copy(status = 2))
                } else if (commitment.shouldReset(currentTime, startDayOfWeekPreference)) {
                    db.commitmentDao().updateCommitment(commitment.copy(completedCount = 0, status = 0))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = requireActivity().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        startDayOfWeekPreference = prefs.getInt("start_day_of_week", Calendar.SUNDAY)
        activeAdapter.updateStartDay(startDayOfWeekPreference)
        pendingAdapter.updateStartDay(startDayOfWeekPreference)
        completedAdapter.updateStartDay(startDayOfWeekPreference)
        
        viewLifecycleOwner.lifecycleScope.launch {
            checkAndResetCommitments()
        }
    }

    private fun showAddEditCommitmentDialog(commitment: Commitment? = null) {
        val context = requireContext()
        val scrollView = ScrollView(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 32)
        }
        scrollView.addView(layout)

        val titleInput = EditText(context).apply {
            hint = "Title"
            setText(commitment?.title ?: "")
        }
        val descInput = EditText(context).apply {
            hint = "Description"
            setText(commitment?.description ?: "")
        }

        val freqSpinner = Spinner(context)
        val frequencies = arrayOf("DAILY", "WEEKLY", "FLEX_WEEKLY", "FLEX_MONTHLY", "MONTHLY")
        freqSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, frequencies)
        freqSpinner.setSelection(frequencies.indexOf(commitment?.frequency ?: "WEEKLY").coerceAtLeast(0))

        val intervalLabel = TextView(context).apply { text = "Every X units" }
        val intervalInput = EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText((commitment?.interval ?: 1).toString())
        }

        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        var selectedStartDate = commitment?.startDate ?: System.currentTimeMillis()
        var selectedEndDate: Long? = commitment?.endDate

        val startDateBtn = Button(context).apply { 
            text = "Starts: ${sdf.format(Date(selectedStartDate))}"
            setAllCaps(false)
        }
        val endDateBtn = Button(context).apply { 
            text = selectedEndDate?.let { "Ends: ${sdf.format(Date(it))}" } ?: "Set Completion Date (Forever)"
            setAllCaps(false)
        }

        startDateBtn.setOnClickListener {
            val c = Calendar.getInstance().apply { timeInMillis = selectedStartDate }
            DatePickerDialog(context, { _, y, m, d ->
                c.set(y, m, d); selectedStartDate = c.timeInMillis
                startDateBtn.text = "Starts: ${sdf.format(c.time)}"
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }

        endDateBtn.setOnClickListener {
            val c = Calendar.getInstance().apply { timeInMillis = selectedEndDate ?: System.currentTimeMillis() }
            DatePickerDialog(context, { _, y, m, d ->
                c.set(y, m, d); selectedEndDate = c.timeInMillis
                endDateBtn.text = "Ends: ${sdf.format(c.time)}"
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }

        val targetSliderContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val targetLabel = TextView(context).apply { text = "Target completions per period:" }
        val targetSlider = Slider(context).apply {
            valueFrom = 1f; valueTo = 7f; stepSize = 1f
            value = commitment?.targetCompletions?.toFloat() ?: 1f
        }
        targetSliderContainer.addView(targetLabel); targetSliderContainer.addView(targetSlider)

        layout.addView(titleInput); layout.addView(descInput)
        layout.addView(TextView(context).apply { text = "Frequency"; setPadding(0, 24, 0, 8) })
        layout.addView(freqSpinner); layout.addView(intervalLabel); layout.addView(intervalInput)
        layout.addView(startDateBtn); layout.addView(endDateBtn); layout.addView(targetSliderContainer)

        freqSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val selected = frequencies[pos]
                targetSliderContainer.visibility = if (selected.startsWith("FLEX")) View.VISIBLE else View.GONE
                if (selected == "FLEX_MONTHLY") {
                    targetSlider.valueTo = 31f
                    targetLabel.text = "Target completions per month:"
                } else if (selected == "FLEX_WEEKLY") {
                    targetSlider.valueTo = 7f
                    targetLabel.text = "Target completions per week:"
                }
                
                when (selected) {
                    "DAILY" -> intervalLabel.text = "Every X days"
                    "WEEKLY" -> intervalLabel.text = "Every X weeks"
                    "MONTHLY" -> intervalLabel.text = "Every X months"
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        AlertDialog.Builder(context)
            .setTitle(if (commitment == null) "New Commitment" else "Edit Commitment")
            .setView(scrollView)
            .setPositiveButton("Save") { _, _ ->
                val title = titleInput.text.toString()
                if (title.isNotBlank()) {
                    val freq = freqSpinner.selectedItem.toString()
                    val newC = (commitment ?: Commitment()).copy(
                        title = title, description = descInput.text.toString(),
                        frequency = freq, interval = intervalInput.text.toString().toIntOrNull() ?: 1,
                        startDate = selectedStartDate, endDate = selectedEndDate,
                        targetCompletions = if (freq.startsWith("FLEX")) targetSlider.value.toInt() else 1,
                        status = 0
                    )
                    if (commitment == null) addCommitment(newC) else updateCommitment(newC)
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun addCommitment(c: Commitment) {
        viewLifecycleOwner.lifecycleScope.launch { db.commitmentDao().insertCommitment(c) }
    }
    
    private fun updateCommitment(c: Commitment) {
        viewLifecycleOwner.lifecycleScope.launch { db.commitmentDao().updateCommitment(c) }
    }
    
    private fun deleteCommitment(c: Commitment) {
        viewLifecycleOwner.lifecycleScope.launch { db.commitmentDao().deleteCommitment(c) }
    }
    
    private fun resetCommitment(c: Commitment) {
        viewLifecycleOwner.lifecycleScope.launch { 
            db.commitmentDao().updateCommitment(c.copy(completedCount = 0, totalCompletions = 0, status = 0, lastCompletionTimestamp = 0)) 
        }
    }

    private fun completeCommitmentForever(c: Commitment) {
        viewLifecycleOwner.lifecycleScope.launch {
            db.commitmentDao().updateCommitment(c.copy(status = 2, lastCompletionTimestamp = System.currentTimeMillis()))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView(); _binding = null
    }
}
