package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemHistoryBinding
import com.example.myapplication.databinding.ItemHistoryHeaderBinding

sealed class HistoryListItem {
    data class Header(val title: String) : HistoryListItem()
    data class Entry(val checkIn: CheckIn) : HistoryListItem()
}

class HistoryAdapter(
    private var items: List<HistoryListItem>,
    private val isTrustedOnly: Boolean,
    private val onEditClick: (CheckIn) -> Unit,
    private val onDeleteClick: (CheckIn) -> Unit,
    private val onShareClick: (CheckIn) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var expandedPosition = -1

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is HistoryListItem.Header -> TYPE_HEADER
            is HistoryListItem.Entry -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val binding = ItemHistoryHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            else -> {
                val binding = ItemHistoryBinding.inflate(inflater, parent, false)
                ItemViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is HeaderViewHolder && item is HistoryListItem.Header) {
            holder.binding.textviewHeaderTitle.text = item.title
        } else if (holder is ItemViewHolder && item is HistoryListItem.Entry) {
            val checkIn = item.checkIn
            val context = holder.itemView.context
            
            holder.binding.textviewHistoryDate.text = checkIn.date
            holder.binding.textviewHistoryScale.text = checkIn.scaleOption
            
            val colorRes = when (checkIn.scaleOption) {
                context.getString(R.string.scale_restoration) -> R.color.color_restoration
                context.getString(R.string.scale_forgetting) -> R.color.color_forgetting
                context.getString(R.string.scale_anxiety) -> R.color.color_anxiety
                context.getString(R.string.scale_speeding) -> R.color.color_speeding
                context.getString(R.string.scale_ticked_off) -> R.color.color_ticked_off
                context.getString(R.string.scale_exhausted) -> R.color.color_exhausted
                context.getString(R.string.scale_relapse) -> R.color.color_relapse
                else -> R.color.purple_500
            }
            holder.binding.textviewHistoryScale.setTextColor(ContextCompat.getColor(context, colorRes))
            
            val isExpanded = position == expandedPosition
            if (checkIn.description.isNotEmpty() && isExpanded) {
                holder.binding.textviewHistoryDescription.text = checkIn.description
                holder.binding.textviewHistoryDescription.visibility = View.VISIBLE
            } else {
                holder.binding.textviewHistoryDescription.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                val prevExpanded = expandedPosition
                expandedPosition = if (isExpanded) -1 else holder.adapterPosition
                notifyItemChanged(prevExpanded)
                notifyItemChanged(expandedPosition)
            }

            holder.binding.buttonEdit.setOnClickListener { onEditClick(checkIn) }
            holder.binding.buttonDelete.setOnClickListener { onDeleteClick(checkIn) }
            holder.binding.buttonShare.setOnClickListener { onShareClick(checkIn) }

            if (isTrustedOnly) {
                holder.binding.buttonShare.setImageResource(android.R.drawable.ic_menu_send)
            } else {
                holder.binding.buttonShare.setImageResource(android.R.drawable.ic_menu_share)
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<HistoryListItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    class HeaderViewHolder(val binding: ItemHistoryHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    class ItemViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)
}
