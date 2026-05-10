package com.example.myapplication

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemCalendarDayBinding

data class CalendarDay(
    val dayOfMonth: String,
    val dateString: String?, // Format: "MMMM dd, yyyy"
    val scaleOption: String? = null,
    val isSelected: Boolean = false,
    val isToday: Boolean = false
)

class CalendarAdapter(
    private val days: List<CalendarDay>,
    private val onDayClick: (CalendarDay) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemCalendarDayBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCalendarDayBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val day = days[position]
        holder.binding.dayLabel.text = day.dayOfMonth

        val context = holder.itemView.context
        
        // Default State
        holder.binding.cardDay.setCardBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
        holder.binding.cardDay.strokeWidth = 0
        holder.binding.dayLabel.setTextColor(ContextCompat.getColor(context, android.R.color.tab_indicator_text))
        holder.binding.dayLabel.setTypeface(null, Typeface.NORMAL)
        holder.binding.cardDay.alpha = 1f

        if (day.dayOfMonth.isEmpty()) {
            holder.binding.cardDay.alpha = 0f
            holder.itemView.setOnClickListener(null)
            return
        }

        // Color coding based on scale
        if (day.scaleOption != null) {
            val colorRes = when (day.scaleOption) {
                context.getString(R.string.scale_restoration) -> R.color.color_restoration
                context.getString(R.string.scale_forgetting) -> R.color.color_forgetting
                context.getString(R.string.scale_anxiety) -> R.color.color_anxiety
                context.getString(R.string.scale_speeding) -> R.color.color_speeding
                context.getString(R.string.scale_ticked_off) -> R.color.color_ticked_off
                context.getString(R.string.scale_exhausted) -> R.color.color_exhausted
                context.getString(R.string.scale_relapse) -> R.color.color_relapse
                else -> null
            }

            colorRes?.let {
                holder.binding.cardDay.setCardBackgroundColor(ContextCompat.getColor(context, it))
                holder.binding.dayLabel.setTextColor(ContextCompat.getColor(context, R.color.white))
            }
        }

        // Highlight Selected Day
        if (day.isSelected) {
            holder.binding.cardDay.strokeWidth = 4
            holder.binding.cardDay.setStrokeColor(ContextCompat.getColorStateList(context, R.color.purple_500))
            holder.binding.dayLabel.setTypeface(null, Typeface.BOLD)
        }

        // Indicator for Today
        if (day.isToday && day.scaleOption == null) {
            holder.binding.dayLabel.setTextColor(ContextCompat.getColor(context, R.color.purple_500))
            holder.binding.dayLabel.setTypeface(null, Typeface.BOLD)
        }

        holder.itemView.setOnClickListener {
            onDayClick(day)
        }
    }

    override fun getItemCount() = days.size
}
