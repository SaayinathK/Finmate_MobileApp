package com.example.financetracker.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.financetracker.R
import com.example.financetracker.databinding.ItemLoanBinding
import com.example.financetracker.models.Loan

class LoanAdapter(
    private val loans: List<Loan>,
    private val section: String = "ALL", // "ALL", "RECEIVED", "GIVVEN"
    private val onReceive: ((Loan) -> Unit)? = null,
    private val onPay: ((Loan) -> Unit)? = null,
    private val onEdit: ((Loan) -> Unit)? = null,
    private val onDelete: ((Loan) -> Unit)? = null
) : RecyclerView.Adapter<LoanAdapter.LoanViewHolder>() {

    inner class LoanViewHolder(private val binding: ItemLoanBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(loan: Loan) {
            binding.personName.text = loan.personName
            binding.amount.text = itemView.context.getString(
                R.string.loan_amount,
                loan.amount.toString()
            )
            binding.note.text = loan.note ?: ""

            // Hide all buttons by default
            binding.receiveButton.visibility = android.view.View.GONE
            binding.payButton.visibility = android.view.View.GONE
            binding.editButton.visibility = android.view.View.GONE
            binding.deleteButton.visibility = android.view.View.GONE

            when (section) {
                "ALL" -> {
                    binding.receiveButton.visibility = android.view.View.VISIBLE
                    binding.payButton.visibility = android.view.View.VISIBLE
                    binding.receiveButton.setOnClickListener { onReceive?.invoke(loan) }
                    binding.payButton.setOnClickListener { onPay?.invoke(loan) }
                }
                "RECEIVED" -> {
                    binding.payButton.visibility = android.view.View.VISIBLE
                    binding.editButton.visibility = android.view.View.VISIBLE
                    binding.payButton.setOnClickListener { onPay?.invoke(loan) }
                    binding.editButton.setOnClickListener { onEdit?.invoke(loan) }
                }
                "GIVVEN" -> {
                    binding.receiveButton.visibility = android.view.View.VISIBLE
                    binding.editButton.visibility = android.view.View.VISIBLE
                    binding.receiveButton.setOnClickListener { onReceive?.invoke(loan) }
                    binding.editButton.setOnClickListener { onEdit?.invoke(loan) }
                }
            }
            binding.deleteButton.visibility = android.view.View.VISIBLE
            binding.deleteButton.setOnClickListener { onDelete?.invoke(loan) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LoanViewHolder {
        val binding = ItemLoanBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LoanViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LoanViewHolder, position: Int) {
        holder.bind(loans[position])
    }

    override fun getItemCount() = loans.size
}
