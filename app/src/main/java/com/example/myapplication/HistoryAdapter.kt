package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemHistoryBinding

class HistoryAdapter(
    private var checkIns: List<CheckIn>,
    private val isTrustedOnly: Boolean,
    private val onEditClick: (CheckIn) -> Unit,
    private val onDeleteClick: (CheckIn) -> Unit,
    private val onShareClick: (CheckIn) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private var expandedPosition = -1

    class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val checkIn = checkIns[position]
        val context = holder.itemView.context
        
        holder.binding.textviewHistoryDate.text = checkIn.date
        holder.binding.textviewHistoryScale.text = checkIn.scaleOption
        
        // Color coding based on scale
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
        
        // Expansion logic
        val isExpanded = position == expandedPosition
        if (checkIn.description.isNotEmpty() && isExpanded) {
            holder.binding.textviewHistoryDescription.text = checkIn.description
            holder.binding.textviewHistoryDescription.visibility = View.VISIBLE
        } else {
            holder.binding.textviewHistoryDescription.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            val prevExpanded = expandedPosition
            expandedPosition = if (isExpanded) -1 else position
            notifyItemChanged(prevExpanded)
            notifyItemChanged(expandedPosition)
        }

        holder.binding.buttonEdit.setOnClickListener {
            onEditClick(checkIn)
        }

        holder.binding.buttonDelete.setOnClickListener {
            onDeleteClick(checkIn)
        }

        holder.binding.buttonShare.setOnClickListener {
            onShareClick(checkIn)
        }

        // Update icon based on sharing mode
        if (isTrustedOnly) {
            holder.binding.buttonShare.setImageResource(android.R.drawable.ic_menu_send)
        } else {
            holder.binding.buttonShare.setImageResource(android.R.drawable.ic_menu_share)
        }
    }

    override fun getItemCount() = checkIns.size

    fun updateData(newCheckIns: List<CheckIn>) {
        this.checkIns = newCheckIns
        notifyDataSetChanged()
    }
}
