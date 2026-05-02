package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemHistoryBinding

class HistoryAdapter(private val checkIns: List<CheckIn>) :
    RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

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
        holder.binding.textviewHistoryDate.text = checkIn.date
        holder.binding.textviewHistoryScale.text = checkIn.scaleOption
        
        if (checkIn.description.isNotEmpty()) {
            holder.binding.textviewHistoryDescription.text = checkIn.description
            holder.binding.textviewHistoryDescription.visibility = View.VISIBLE
        } else {
            holder.binding.textviewHistoryDescription.visibility = View.GONE
        }
    }

    override fun getItemCount() = checkIns.size
}
