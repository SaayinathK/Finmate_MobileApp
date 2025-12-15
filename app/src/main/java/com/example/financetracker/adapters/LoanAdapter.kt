package com.example.financetracker.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.financetracker.R
import com.example.financetracker.databinding.ItemLoanBinding
import com.example.financetracker.models.Loan

class LoanAdapter(
    private val loans: List<Loan>,
    private val onAction: ((Loan) -> Unit)? = null,
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

            // Set action button text depending on loan type
            val actionText = when (loan.type) {
                "RECEIVE" -> "Received"
                "PAY" -> "Paid"
                else -> "Action"
            }
            binding.actionButton.text = actionText
            binding.actionButton.setOnClickListener {
                onAction?.invoke(loan)
            }

            binding.deleteButton.setOnClickListener {
                onDelete?.invoke(loan)
            }
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
