package com.fasterscale.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import androidx.core.content.ContextCompat

class ScaleOptionAdapter(
    context: Context,
    private val options: List<ScaleOption>
) : ArrayAdapter<ScaleOption>(context, R.layout.item_dropdown_scale, options) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromResource(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromResource(position, convertView, parent)
    }

    private fun createViewFromResource(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(
            R.layout.item_dropdown_scale, parent, false
        )

        val option = getItem(position)
        val titleView = view.findViewById<TextView>(android.R.id.text1)
        val descriptionView = view.findViewById<TextView>(R.id.text_description)

        titleView?.text = option?.title
        descriptionView?.text = option?.description

        // Apply scale coloring to the title
        option?.let {
            val colorRes = when (it.title) {
                context.getString(R.string.scale_restoration) -> R.color.color_restoration
                context.getString(R.string.scale_forgetting) -> R.color.color_forgetting
                context.getString(R.string.scale_anxiety) -> R.color.color_anxiety
                context.getString(R.string.scale_speeding) -> R.color.color_speeding
                context.getString(R.string.scale_ticked_off) -> R.color.color_ticked_off
                context.getString(R.string.scale_exhausted) -> R.color.color_exhausted
                context.getString(R.string.scale_relapse) -> R.color.color_relapse
                else -> null
            }
            colorRes?.let { resId ->
                titleView?.setTextColor(ContextCompat.getColor(context, resId))
            }
        }

        return view
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                results.values = options
                results.count = options.size
                return results
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                notifyDataSetChanged()
            }

            override fun convertResultToString(resultValue: Any?): CharSequence {
                return (resultValue as? ScaleOption)?.title ?: ""
            }
        }
    }
}

data class ScaleOption(val title: String, val description: String) {
    override fun toString(): String = title
}
