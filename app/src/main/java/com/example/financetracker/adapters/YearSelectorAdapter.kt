package com.example.financetracker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.financetracker.R

class YearSelectorAdapter(
    private val years: List<String>,
    private var selectedYear: String,
    private val onYearSelected: (String) -> Unit
) : RecyclerView.Adapter<YearSelectorAdapter.YearViewHolder>() {

    inner class YearViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val yearText: TextView = itemView.findViewById(R.id.textYear)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): YearViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_year, parent, false)
        return YearViewHolder(view)
    }

    override fun onBindViewHolder(holder: YearViewHolder, position: Int) {
        val year = years[position]
        holder.yearText.text = year

        val context = holder.itemView.context
        if (year == selectedYear) {
            holder.yearText.setBackgroundResource(R.drawable.bg_selected_year)
            holder.yearText.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        } else {
            holder.yearText.setBackgroundResource(R.drawable.bg_default_year)
            holder.yearText.setTextColor(ContextCompat.getColor(context, android.R.color.black))
        }

        holder.yearText.setOnClickListener {
            selectedYear = year
            onYearSelected(year)
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = years.size
}
