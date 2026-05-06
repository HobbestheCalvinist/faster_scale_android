package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.databinding.FragmentCommitmentBinding
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch

class CommitmentFragment : Fragment() {

    private var _binding: FragmentCommitmentBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase

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

        val activeAdapter = CommitmentAdapter(
            onUpdateCommitment = { commitment -> updateCommitment(commitment) },
            onEdit = { commitment -> showAddEditCommitmentDialog(commitment) },
            onDelete = { commitment -> deleteCommitment(commitment) },
            onReset = { commitment -> resetCommitment(commitment) }
        )

        val completedAdapter = CommitmentAdapter(
            onUpdateCommitment = { commitment -> updateCommitment(commitment) },
            onEdit = { commitment -> showAddEditCommitmentDialog(commitment) },
            onDelete = { commitment -> deleteCommitment(commitment) },
            onReset = { commitment -> resetCommitment(commitment) }
        )

        binding.recyclerviewActiveCommitments.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = activeAdapter
        }

        binding.recyclerviewCompletedCommitments.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = completedAdapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    db.commitmentDao().getActiveCommitments().collect { commitments ->
                        activeAdapter.submitList(commitments)
                        binding.textviewActiveHeader.visibility = if (commitments.isEmpty()) View.GONE else View.VISIBLE
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

    private fun showAddEditCommitmentDialog(commitment: Commitment? = null) {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 32)
        }

        val titleInput = EditText(context).apply {
            hint = "Title"
            setText(commitment?.title ?: "")
        }
        
        val descInput = EditText(context).apply {
            hint = "Description"
            setText(commitment?.description ?: "")
        }

        val sliderLabel = TextView(context).apply {
            text = "Number of completions required (1-7):"
            setPadding(0, 32, 0, 8)
        }

        val iterationsSlider = Slider(context).apply {
            valueFrom = 1f
            valueTo = 7f
            stepSize = 1f
            value = commitment?.targetCompletions?.toFloat() ?: 1f
        }

        layout.addView(titleInput)
        layout.addView(descInput)
        layout.addView(sliderLabel)
        layout.addView(iterationsSlider)

        AlertDialog.Builder(context)
            .setTitle(if (commitment == null) "New Commitment" else "Edit Commitment")
            .setView(layout)
            .setPositiveButton(if (commitment == null) "Add" else "Save") { _, _ ->
                val title = titleInput.text.toString()
                val desc = descInput.text.toString()
                val target = iterationsSlider.value.toInt()
                
                if (title.isNotBlank()) {
                    if (commitment == null) {
                        addCommitment(title, desc, target)
                    } else {
                        updateCommitment(commitment.copy(title = title, description = desc, targetCompletions = target))
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addCommitment(title: String, desc: String, target: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            db.commitmentDao().insertCommitment(
                Commitment(title = title, description = desc, targetCompletions = target)
            )
        }
    }

    private fun updateCommitment(commitment: Commitment) {
        viewLifecycleOwner.lifecycleScope.launch {
            db.commitmentDao().updateCommitment(commitment)
        }
    }

    private fun resetCommitment(commitment: Commitment) {
        viewLifecycleOwner.lifecycleScope.launch {
            db.commitmentDao().updateCommitment(
                commitment.copy(currentCompletions = 0, isCompleted = false)
            )
        }
    }

    private fun deleteCommitment(commitment: Commitment) {
        viewLifecycleOwner.lifecycleScope.launch {
            db.commitmentDao().deleteCommitment(commitment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
