package com.example.financetracker

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.financetracker.databinding.ItemTransactionBinding

class TransactionAdapter(
    private val items: MutableList<Transaction>,
    private val onDelete: (Int) -> Unit,
    private val onEdit: (Int) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    inner class TransactionViewHolder(val binding: ItemTransactionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.editButton.setOnClickListener {
                val position = absoluteAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onEdit(position)
                }
            }
            binding.deleteButton.setOnClickListener {
                val position = absoluteAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDelete(position)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val item = items[position]
        with(holder.binding) {
            titleText.text = item.title
            dateText.text = item.date
            typeText.text = item.type
            categoryText.text = item.category

            // Amount formatting
            val prefix = if (item.type == "Income") "+" else "-"
            val color = if (item.type == "Income") "#4CAF50" else "#F44336"
            amountText.setTextColor(Color.parseColor(color))
            amountText.text = "$prefix Rs. ${"%.2f".format(kotlin.math.abs(item.amount))}"
        }
    }

    override fun getItemCount(): Int = items.size
}
