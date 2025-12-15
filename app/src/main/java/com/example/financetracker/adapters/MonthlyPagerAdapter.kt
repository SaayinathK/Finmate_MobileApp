package com.example.financetracker.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.financetracker.fragments.MonthlyTransactionFragment
import com.example.financetracker.models.Transaction

class MonthlyPagerAdapter(
    activity: FragmentActivity,
    private val months: List<String>,
    private val monthToTransactions: Map<String, List<Transaction>>
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = months.size

    override fun createFragment(position: Int): Fragment {
        val month = months[position]
        val transactions = monthToTransactions[month] ?: emptyList()
        return MonthlyTransactionFragment.newInstance(month, transactions)
    }
}
