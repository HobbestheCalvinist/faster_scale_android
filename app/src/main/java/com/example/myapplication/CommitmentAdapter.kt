package com.fasterscale.app

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fasterscale.app.databinding.ItemCommitmentBinding
import java.util.Calendar

class CommitmentAdapter(
    private var startDayOfWeek: Int,
    private val onUpdateCommitment: (Commitment) -> Unit,
    private val onEdit: (Commitment) -> Unit,
    private val onDelete: (Commitment) -> Unit,
    private val onReset: (Commitment) -> Unit
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
        val context = holder.itemView.context

        holder.binding.textviewCommitmentTitle.text = "${commitment.title} (${commitment.targetCompletions} x Week)"
        holder.binding.textviewCommitmentDescription.text = commitment.description
        
        // Expand/Collapse logic
        val isExpanded = position == expandedPosition
        holder.binding.layoutExpandableContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.binding.imageviewExpandArrow.rotation = if (isExpanded) 180f else 0f
        
        holder.itemView.setOnClickListener {
            val prevExpanded = expandedPosition
            expandedPosition = if (isExpanded) -1 else holder.adapterPosition
            notifyItemChanged(prevExpanded)
            notifyItemChanged(expandedPosition)
        }

        // Handle completion checkboxes - 7 independent checkboxes with labels
        holder.binding.layoutCompletionCheckboxes.removeAllViews()
        
        val dayLabels = getDayLabels(startDayOfWeek)
        
        for (i in 0 until 7) {
            val dayContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val dayLabel = TextView(context).apply {
                text = dayLabels[i]
                textSize = 10f
                gravity = Gravity.CENTER
            }

            val checkBox = CheckBox(context).apply {
                // Increased size
                scaleX = 1.1f
                scaleY = 1.1f
                // Minimize padding to keep on one line
                setPadding(0, 0, 0, 0)
                minWidth = 0
                minHeight = 0

                isChecked = (commitment.completedDaysMask and (1 shl i)) != 0
                
                setOnCheckedChangeListener { _, isChecked ->
                    val newMask = if (isChecked) {
                        commitment.completedDaysMask or (1 shl i)
                    } else {
                        commitment.completedDaysMask and (1 shl i).inv()
                    }
                    
                    val completedCount = Integer.bitCount(newMask)
                    val isNowCompleted = completedCount >= commitment.targetCompletions
                    
                    if (newMask != commitment.completedDaysMask || isNowCompleted != commitment.isCompleted) {
                        onUpdateCommitment(commitment.copy(
                            completedDaysMask = newMask,
                            isCompleted = isNowCompleted
                        ))
                    }
                }
            }

            dayContainer.addView(dayLabel)
            dayContainer.addView(checkBox)
            holder.binding.layoutCompletionCheckboxes.addView(dayContainer)
        }

        holder.binding.buttonEdit.setOnClickListener { onEdit(commitment) }
        holder.binding.buttonDelete.setOnClickListener { onDelete(commitment) }
        holder.binding.buttonReset.setOnClickListener { onReset(commitment) }
        
        holder.binding.root.alpha = if (commitment.isCompleted) 0.6f else 1.0f
    }

    private fun getDayLabels(startDay: Int): List<String> {
        val days = listOf("S", "M", "T", "W", "T", "F", "S")
        // startDay is Calendar.SUNDAY (1) to Calendar.SATURDAY (7)
        val startIndex = startDay - 1
        val result = mutableListOf<String>()
        for (i in 0 until 7) {
            result.add(days[(startIndex + i) % 7])
        }
        return result
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
