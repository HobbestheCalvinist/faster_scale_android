package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemCommitmentBinding

class CommitmentAdapter(
    private val onToggleComplete: (Commitment) -> Unit,
    private val onUpdateCompletions: (Commitment) -> Unit,
    private val onEdit: (Commitment) -> Unit,
    private val onDelete: (Commitment) -> Unit,
    private val onReset: (Commitment) -> Unit
) : ListAdapter<Commitment, CommitmentAdapter.ViewHolder>(CommitmentDiffCallback()) {

    class ViewHolder(val binding: ItemCommitmentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCommitmentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val commitment = getItem(position)
        val context = holder.itemView.context

        holder.binding.textviewCommitmentTitle.text = commitment.title
        holder.binding.textviewCommitmentDescription.text = commitment.description
        
        // Master checkbox for overall completion
        holder.binding.checkboxMaster.setOnCheckedChangeListener(null)
        holder.binding.checkboxMaster.isChecked = commitment.isCompleted
        holder.binding.checkboxMaster.setOnCheckedChangeListener { _, isChecked ->
            onToggleComplete(commitment.copy(isCompleted = isChecked))
        }

        // Handle completion checkboxes
        holder.binding.layoutCompletionCheckboxes.removeAllViews()
        for (i in 1..commitment.targetCompletions) {
            val checkBox = CheckBox(context).apply {
                isChecked = i <= commitment.currentCompletions
                setOnCheckedChangeListener { _, isChecked ->
                    val newCount = if (isChecked) {
                        // Check all boxes up to this one
                        maxOf(commitment.currentCompletions, i)
                    } else {
                        // Uncheck all boxes from this one onwards
                        minOf(commitment.currentCompletions, i - 1)
                    }
                    if (newCount != commitment.currentCompletions) {
                        onUpdateCompletions(commitment.copy(currentCompletions = newCount))
                    }
                }
            }
            holder.binding.layoutCompletionCheckboxes.addView(checkBox)
        }

        holder.binding.buttonEdit.setOnClickListener { onEdit(commitment) }
        holder.binding.buttonDelete.setOnClickListener { onDelete(commitment) }
        holder.binding.buttonReset.setOnClickListener { onReset(commitment) }
        
        // Visual feedback for completed items
        holder.binding.root.alpha = if (commitment.isCompleted) 0.6f else 1.0f
    }

    class CommitmentDiffCallback : DiffUtil.ItemCallback<Commitment>() {
        override fun areItemsTheSame(oldItem: Commitment, newItem: Commitment): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Commitment, newItem: Commitment): Boolean = oldItem == newItem
    }
}
