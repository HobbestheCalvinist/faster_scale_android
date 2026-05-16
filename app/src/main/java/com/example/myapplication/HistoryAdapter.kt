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
    data class Entry(val checkIn: CheckIn, val groupTitle: String) : HistoryListItem()
}

class HistoryAdapter(
    private var allItems: List<HistoryListItem>,
    private var isTrustedOnly: Boolean,
    private val onEditClick: (CheckIn) -> Unit,
    private val onDeleteClick: (CheckIn) -> Unit,
    private val onShareClick: (CheckIn) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var expandedEntryPosition = -1
    private var visibleItems: List<HistoryListItem> = emptyList()
    private val collapsedGroups = mutableSetOf<String>()

    init {
        updateVisibleItems()
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    private fun updateVisibleItems() {
        visibleItems = allItems.filter { item ->
            when (item) {
                is HistoryListItem.Header -> true
                is HistoryListItem.Entry -> !collapsedGroups.contains(item.groupTitle)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (visibleItems[position]) {
            is HistoryListItem.Header -> TYPE_HEADER
            is HistoryListItem.Entry -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(ItemHistoryHeaderBinding.inflate(inflater, parent, false))
            else -> ItemViewHolder(ItemHistoryBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = visibleItems[position]
        if (holder is HeaderViewHolder && item is HistoryListItem.Header) {
            holder.binding.textviewHeaderTitle.text = item.title
            val isCollapsed = collapsedGroups.contains(item.title)
            
            // Visual indicator for expand/collapse
            holder.binding.textviewHeaderTitle.setCompoundDrawablesWithIntrinsicBounds(
                0, 0, if (isCollapsed) android.R.drawable.arrow_down_float else android.R.drawable.arrow_up_float, 0
            )
            
            holder.itemView.setOnClickListener {
                if (collapsedGroups.contains(item.title)) {
                    collapsedGroups.remove(item.title)
                } else {
                    collapsedGroups.add(item.title)
                }
                updateVisibleItems()
                notifyDataSetChanged()
            }
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
            
            if (checkIn.callMade) {
                holder.binding.textviewHistoryCall.visibility = View.VISIBLE
                val completedIds = checkIn.completedScheduleIds.split(",").filter { it.isNotBlank() }
                
                if (completedIds.size > 1) {
                    holder.binding.textviewHistoryCall.text = context.getString(R.string.label_calls_made_count, completedIds.size)
                } else {
                    holder.binding.textviewHistoryCall.setText(R.string.label_call_made_simple)
                }
                
                val iconRes = if (checkIn.isInboundCall) R.drawable.ic_call_inbound else R.drawable.ic_call_outbound
                val icon = ContextCompat.getDrawable(context, iconRes)?.apply {
                    val size = (holder.binding.textviewHistoryCall.textSize * 1.1f).toInt()
                    setBounds(0, 0, size, size)
                    setTint(ContextCompat.getColor(context, R.color.purple_500))
                }
                holder.binding.textviewHistoryCall.setCompoundDrawables(icon, null, null, null)
                holder.binding.textviewHistoryCall.compoundDrawablePadding = 8
            } else {
                holder.binding.textviewHistoryCall.visibility = View.GONE
                holder.binding.textviewHistoryCall.setCompoundDrawables(null, null, null, null)
            }

            val isExpanded = position == expandedEntryPosition
            if (checkIn.description.isNotEmpty() && isExpanded) {
                holder.binding.textviewHistoryDescription.text = checkIn.description
                holder.binding.textviewHistoryDescription.visibility = View.VISIBLE
            } else {
                holder.binding.textviewHistoryDescription.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                val prevExpanded = expandedEntryPosition
                expandedEntryPosition = if (isExpanded) -1 else holder.adapterPosition
                notifyItemChanged(prevExpanded)
                notifyItemChanged(expandedEntryPosition)
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

    override fun getItemCount() = visibleItems.size

    fun updateData(newItems: List<HistoryListItem>, isTrustedOnly: Boolean) {
        this.allItems = newItems
        this.isTrustedOnly = isTrustedOnly
        updateVisibleItems()
        notifyDataSetChanged()
    }
    
    fun collapseGroups(groupTitles: List<String>) {
        collapsedGroups.addAll(groupTitles)
        updateVisibleItems()
        notifyDataSetChanged()
    }

    class HeaderViewHolder(val binding: ItemHistoryHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    class ItemViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)
}
