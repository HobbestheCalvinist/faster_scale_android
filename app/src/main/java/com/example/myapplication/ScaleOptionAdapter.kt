package com.example.myapplication

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView

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
