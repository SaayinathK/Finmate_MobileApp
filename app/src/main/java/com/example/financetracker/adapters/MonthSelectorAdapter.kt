package com.example.financetracker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.financetracker.R

class MonthSelectorAdapter(
    private val months: List<String>,
    private var selectedMonth: String,
    private val onMonthSelected: (String) -> Unit
) : RecyclerView.Adapter<MonthSelectorAdapter.MonthViewHolder>() {

    inner class MonthViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val monthText: TextView = itemView.findViewById(R.id.textMonth)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_month, parent, false)
        return MonthViewHolder(view)
    }

    override fun onBindViewHolder(holder: MonthViewHolder, position: Int) {
        val month = months[position]
        holder.monthText.text = month

        // Highlight selected month
        val context = holder.itemView.context
        if (month == selectedMonth) {
            holder.monthText.setBackgroundResource(R.drawable.selected_month_bg)
            holder.monthText.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        } else {
            holder.monthText.setBackgroundResource(R.drawable.default_month_bg)
            holder.monthText.setTextColor(ContextCompat.getColor(context, android.R.color.black))
        }

        holder.monthText.setOnClickListener {
            selectedMonth = month
            onMonthSelected(month)
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = months.size
}