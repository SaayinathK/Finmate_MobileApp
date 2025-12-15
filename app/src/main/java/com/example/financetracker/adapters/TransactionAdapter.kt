package com.example.financetracker.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.financetracker.databinding.ItemTransactionBinding
import com.example.financetracker.models.Transaction

class TransactionAdapter(
    private var items: List<Transaction>,
    private val onDelete: (Int) -> Unit,
    private val onEdit: (Int) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    fun updateList(newItems: List<Transaction>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class TransactionViewHolder(private val binding: ItemTransactionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(transaction: Transaction, position: Int) {
            binding.titleText.text = transaction.title
            binding.dateText.text = transaction.date
            binding.categoryText.text = transaction.category
            binding.typeText.text = transaction.type

            val isIncome = transaction.type.equals("Income", ignoreCase = true)
            val prefix = if (isIncome) "+" else "-"
            val color = if (isIncome) "#4CAF50" else "#F44336"

            binding.amountText.setTextColor(Color.parseColor(color))
            binding.amountText.text = "$prefix Rs. %.2f".format(kotlin.math.abs(transaction.amount))

            binding.editButton.setOnClickListener { onEdit(position) }
            binding.deleteButton.setOnClickListener { onDelete(position) }
        }
    }
}
