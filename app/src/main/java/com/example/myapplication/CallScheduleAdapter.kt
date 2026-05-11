package com.example.myapplication

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemCallScheduleBinding

class CallScheduleAdapter(
    private val onEditClick: (CallSchedule) -> Unit,
    private val onDeleteClick: (CallSchedule) -> Unit,
    private val onCallClick: (CallSchedule) -> Unit
) : ListAdapter<CallSchedule, CallScheduleAdapter.ViewHolder>(CallScheduleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCallScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemCallScheduleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(schedule: CallSchedule) {
            val context = binding.root.context
            binding.textviewCombinedInfo.text = context.getString(
                R.string.call_schedule_combined_format,
                schedule.contactName,
                schedule.dayOfWeek,
                schedule.time
            )
            
            binding.buttonCall.setOnClickListener { onCallClick(schedule) }
            binding.buttonEdit.setOnClickListener { onEditClick(schedule) }
            binding.buttonDelete.setOnClickListener { onDeleteClick(schedule) }
        }
    }

    class CallScheduleDiffCallback : DiffUtil.ItemCallback<CallSchedule>() {
        override fun areItemsTheSame(oldItem: CallSchedule, newItem: CallSchedule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CallSchedule, newItem: CallSchedule): Boolean {
            return oldItem == newItem
        }
    }
}
