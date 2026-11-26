package com.fasterscale.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fasterscale.app.databinding.ItemCommitmentBinding

class CommitmentAdapter(
    private var startDayOfWeek: Int,
    private val onUpdateCommitment: (Commitment) -> Unit,
    private val onEdit: (Commitment) -> Unit,
    private val onDelete: (Commitment) -> Unit,
    private val onReset: (Commitment) -> Unit,
    private val onCompleteForever: (Commitment) -> Unit
) : ListAdapter<Commitment, CommitmentAdapter.ViewHolder>(CommitmentDiffCallback()) {

    private var expandedPosition = -1

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
        
        holder.binding.textviewCommitmentTitle.text = commitment.title
        holder.binding.textviewCommitmentDescription.text = 
            "${commitment.description}\n${commitment.getScheduleDescription()}\n${commitment.getStatusDescription()}"
        
        // Corrected ViewBinding reference for the frequency label on the right
        holder.binding.textviewCommitmentFrequencyLabel.text = when (commitment.frequency) {
            "DAILY" -> "Daily"
            "WEEKLY" -> "Weekly"
            "MONTHLY" -> "Monthly"
            "FLEX_WEEKLY" -> "Flexible Weekly"
            "FLEX_MONTHLY" -> "Flexible Monthly"
            else -> commitment.frequency
        }

        val isExpanded = position == expandedPosition
        holder.binding.layoutExpandableContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.binding.imageviewExpandArrow.rotation = if (isExpanded) 180f else 0f
        
        holder.itemView.setOnClickListener {
            val prevExpanded = expandedPosition
            expandedPosition = if (isExpanded) -1 else holder.adapterPosition
            notifyItemChanged(prevExpanded)
            notifyItemChanged(expandedPosition)
        }

        holder.binding.layoutCompletionCheckboxes.removeAllViews()
        
        // Only show incremental checkboxes if status is Active (0)
        if (commitment.status == 0) {
            setupGenericCompletions(holder, commitment)
        }

        // Complete Forever Button
        holder.binding.buttonComplete.visibility = if (commitment.status != 2) View.VISIBLE else View.GONE
        holder.binding.buttonComplete.setOnClickListener { onCompleteForever(commitment) }
        
        holder.binding.buttonEdit.setOnClickListener { onEdit(commitment) }
        holder.binding.buttonDelete.setOnClickListener { onDelete(commitment) }
        holder.binding.buttonReset.setOnClickListener { onReset(commitment) }
        
        // Visual cue for non-active items
        holder.binding.root.alpha = if (commitment.status != 0) 0.6f else 1.0f
    }

    private fun setupGenericCompletions(holder: ViewHolder, commitment: Commitment) {
        val context = holder.itemView.context
        val target = commitment.targetCompletions
        
        for (i in 0 until target) {
            val checkBox = CheckBox(context).apply {
                isChecked = i < commitment.completedCount
                setOnCheckedChangeListener { _, isChecked ->
                    var newCount = commitment.completedCount
                    if (isChecked && i == newCount) {
                        newCount++
                    } else if (!isChecked && i == newCount - 1) {
                        newCount--
                    } else {
                        // Force correct order
                        this.isChecked = i < commitment.completedCount
                        return@setOnCheckedChangeListener
                    }
                    
                    val isIterationMet = newCount >= commitment.targetCompletions
                    // If target met, move to Pending (1)
                    val newStatus = if (isIterationMet) 1 else 0
                    
                    onUpdateCommitment(commitment.copy(
                        completedCount = newCount,
                        totalCompletions = if (isChecked) commitment.totalCompletions + 1 else commitment.totalCompletions - 1,
                        lastCompletionTimestamp = if (isChecked) System.currentTimeMillis() else commitment.lastCompletionTimestamp,
                        status = newStatus
                    ))
                }
            }
            holder.binding.layoutCompletionCheckboxes.addView(checkBox)
        }
    }

    fun updateStartDay(newStartDay: Int) {
        if (this.startDayOfWeek != newStartDay) {
            this.startDayOfWeek = newStartDay
            notifyDataSetChanged()
        }
    }

    class CommitmentDiffCallback : DiffUtil.ItemCallback<Commitment>() {
        override fun areItemsTheSame(oldItem: Commitment, newItem: Commitment): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Commitment, newItem: Commitment): Boolean = oldItem == newItem
    }
}
