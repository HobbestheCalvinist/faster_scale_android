package com.example.myapplication

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemCallHeaderBinding
import com.example.myapplication.databinding.ItemCallScheduleBinding

sealed class CallScheduleListItem {
    data class Header(val title: String) : CallScheduleListItem()
    data class Item(val schedule: CallSchedule) : CallScheduleListItem()
}

class CallScheduleAdapter(
    private val onEditClick: (CallSchedule) -> Unit,
    private val onDeleteClick: (CallSchedule) -> Unit,
    private val onCallClick: (CallSchedule) -> Unit
) : ListAdapter<CallScheduleListItem, RecyclerView.ViewHolder>(CallScheduleDiffCallback()) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is CallScheduleListItem.Header -> TYPE_HEADER
            is CallScheduleListItem.Item -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val binding = ItemCallHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                HeaderViewHolder(binding)
            }
            else -> {
                val binding = ItemCallScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ItemViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is CallScheduleListItem.Header -> (holder as HeaderViewHolder).bind(item.title)
            is CallScheduleListItem.Item -> (holder as ItemViewHolder).bind(item.schedule)
        }
    }

    inner class HeaderViewHolder(private val binding: ItemCallHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.textviewHeader.text = title
        }
    }

    inner class ItemViewHolder(private val binding: ItemCallScheduleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(schedule: CallSchedule) {
            binding.textviewContactName.text = schedule.contactName
            binding.textviewCallDetails.text = binding.root.context.getString(
                R.string.call_schedule_item_format,
                schedule.dayOfWeek,
                schedule.time
            )
            
            binding.buttonCall.setOnClickListener { onCallClick(schedule) }
            binding.buttonEdit.setOnClickListener { onEditClick(schedule) }
            binding.buttonDelete.setOnClickListener { onDeleteClick(schedule) }
        }
    }

    class CallScheduleDiffCallback : DiffUtil.ItemCallback<CallScheduleListItem>() {
        override fun areItemsTheSame(oldItem: CallScheduleListItem, newItem: CallScheduleListItem): Boolean {
            return if (oldItem is CallScheduleListItem.Item && newItem is CallScheduleListItem.Item) {
                oldItem.schedule.id == newItem.schedule.id
            } else if (oldItem is CallScheduleListItem.Header && newItem is CallScheduleListItem.Header) {
                oldItem.title == newItem.title
            } else {
                false
            }
        }

        override fun areContentsTheSame(oldItem: CallScheduleListItem, newItem: CallScheduleListItem): Boolean {
            return oldItem == newItem
        }
    }
}
