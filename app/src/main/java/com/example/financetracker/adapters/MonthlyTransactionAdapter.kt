package com.example.financetracker.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.financetracker.models.MonthlyTransactionItem
import com.example.financetracker.databinding.ItemTransactionBinding
import com.example.financetracker.databinding.ItemTransactionHeaderBinding
import com.example.financetracker.models.Transaction

class MonthlyTransactionAdapter(
    private var items: List<MonthlyTransactionItem>,
    private val onDelete: (Transaction) -> Unit,
    private val onEdit: (Transaction) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_TRANSACTION = 1
    }

    fun updateList(newItems: List<MonthlyTransactionItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is MonthlyTransactionItem.Header -> TYPE_HEADER
            is MonthlyTransactionItem.TransactionItem -> TYPE_TRANSACTION
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val binding = ItemTransactionHeaderBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            HeaderViewHolder(binding)
        } else {
            val binding = ItemTransactionBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            TransactionViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is MonthlyTransactionItem.Header -> (holder as HeaderViewHolder).bind(item)
            is MonthlyTransactionItem.TransactionItem -> (holder as TransactionViewHolder).bind(item.transaction)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class HeaderViewHolder(private val binding: ItemTransactionHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(header: MonthlyTransactionItem.Header) {
            val parts = header.month.split(" ")
            if (parts.size == 2) {
                binding.headerMonth.text = parts[0]
                binding.headerYear.text = parts[1]
            } else {
                binding.headerMonth.text = header.month
                binding.headerYear.text = ""
            }
        }
    }

    inner class TransactionViewHolder(private val binding: ItemTransactionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.editButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = items[position] as? MonthlyTransactionItem.TransactionItem
                    item?.let { onEdit(it.transaction) }
                }
            }

            binding.deleteButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = items[position] as? MonthlyTransactionItem.TransactionItem
                    item?.let { onDelete(it.transaction) }
                }
            }
        }

        fun bind(transaction: Transaction) = with(binding) {
            titleText.text = transaction.title
            dateText.text = transaction.date
            typeText.text = transaction.type
            categoryText.text = transaction.category

            val isIncome = transaction.type.equals("Income", ignoreCase = true)
            val prefix = if (isIncome) "+" else "-"
            val color = if (isIncome) "#4CAF50" else "#F44336"

            amountText.setTextColor(Color.parseColor(color))
            amountText.text = "$prefix Rs. %.2f".format(kotlin.math.abs(transaction.amount))
        }
    }
}
