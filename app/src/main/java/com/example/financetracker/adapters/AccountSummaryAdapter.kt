package com.example.financetracker.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.financetracker.databinding.ItemAccountSummaryBinding
import com.example.financetracker.models.AccountSummary

class AccountSummaryAdapter(
    private val items: List<AccountSummary>,
    private val onReceive: ((AccountSummary) -> Unit)? = null,
    private val onPay: ((AccountSummary) -> Unit)? = null
) : RecyclerView.Adapter<AccountSummaryAdapter.Holder>() {

    class Holder(private val binding: ItemAccountSummaryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(s: AccountSummary, onReceive: ((AccountSummary) -> Unit)?, onPay: ((AccountSummary) -> Unit)?) {
            binding.personName.text = s.personName
            binding.totalReceive.text = "+ Rs. ${String.format("%.2f", s.totalReceive)}"
            binding.totalPay.text = "- Rs. ${String.format("%.2f", s.totalPay)}"
            binding.net.text = "Net: Rs. ${String.format("%.2f", s.net)}"

            binding.btnReceive.setOnClickListener { onReceive?.invoke(s) }
            binding.btnPay.setOnClickListener { onPay?.invoke(s) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemAccountSummaryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], onReceive, onPay)
    }

    override fun getItemCount() = items.size
}
