package com.example.myapplication

import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemCalendarDayBinding

data class CalendarDay(
    val dayOfMonth: String,
    val dateString: String?, // Format: "MMMM dd, yyyy"
    val scaleOption: String? = null,
    val isSelected: Boolean = false,
    val isToday: Boolean = false,
    val hasCall: Boolean = false,
    val isInbound: Boolean = false
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

    private fun getThemeColor(context: android.content.Context, @AttrRes attrRes: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val day = days[position]
        holder.binding.dayLabel.text = day.dayOfMonth

        val context = holder.itemView.context
        val colorOnSurface = getThemeColor(context, com.google.android.material.R.attr.colorOnSurface)
        val colorPrimary = getThemeColor(context, com.google.android.material.R.attr.colorPrimary)
        
        // Default State
        holder.binding.cardDay.setCardBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
        holder.binding.cardDay.strokeWidth = 0
        holder.binding.dayLabel.setTextColor(colorOnSurface)
        holder.binding.dayLabel.setTypeface(null, Typeface.NORMAL)
        holder.binding.cardDay.alpha = 1f
        holder.binding.dayIcon.visibility = View.GONE

        if (day.dayOfMonth.isEmpty()) {
            holder.binding.cardDay.alpha = 0f
            holder.itemView.setOnClickListener(null)
            return
        }

        // Color coding based on scale
        if (day.scaleOption != null) {
            val colorRes = when {
                day.scaleOption.contains("Restoration", ignoreCase = true) -> R.color.color_restoration
                day.scaleOption.contains("Forgetting", ignoreCase = true) -> R.color.color_forgetting
                day.scaleOption.contains("Anxiety", ignoreCase = true) -> R.color.color_anxiety
                day.scaleOption.contains("Speeding", ignoreCase = true) -> R.color.color_speeding
                day.scaleOption.contains("Ticked", ignoreCase = true) -> R.color.color_ticked_off
                day.scaleOption.contains("Exhausted", ignoreCase = true) -> R.color.color_exhausted
                day.scaleOption.contains("Relapse", ignoreCase = true) -> R.color.color_relapse
                else -> null
            }

            colorRes?.let {
                holder.binding.cardDay.setCardBackgroundColor(ContextCompat.getColor(context, it))
                holder.binding.dayLabel.setTextColor(ContextCompat.getColor(context, android.R.color.white))
            }
        }

        // Call Icon
        if (day.hasCall) {
            holder.binding.dayIcon.visibility = View.VISIBLE
            val iconRes = if (day.isInbound) R.drawable.ic_call_inbound else R.drawable.ic_call_outbound
            holder.binding.dayIcon.setImageResource(iconRes)

            val iconTint = if (day.scaleOption != null) {
                ContextCompat.getColor(context, android.R.color.white)
            } else {
                colorPrimary
            }
            holder.binding.dayIcon.setColorFilter(iconTint)
        }

        // Highlight Selected Day
        if (day.isSelected) {
            holder.binding.cardDay.strokeWidth = 4
            holder.binding.cardDay.setStrokeColor(android.content.res.ColorStateList.valueOf(colorPrimary))
            holder.binding.dayLabel.setTypeface(null, Typeface.BOLD)
        }

        // Indicator for Today
        if (day.isToday && day.scaleOption == null) {
            holder.binding.dayLabel.setTextColor(colorPrimary)
            holder.binding.dayLabel.setTypeface(null, Typeface.BOLD)
        }

        holder.itemView.setOnClickListener {
            onDayClick(day)
        }
    }

    override fun getItemCount() = days.size
}
