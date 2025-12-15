package com.example.financetracker.activities

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.financetracker.fragments.LoansListFragment

class LoansPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
    private val modes = listOf("ALL", "RECEIVE", "PAY")
    private val fragments = ArrayList<LoansListFragment?>(modes.size)

    init {
        for (i in modes.indices) fragments.add(null)
    }

    override fun getItemCount() = modes.size

    override fun createFragment(position: Int): Fragment {
        val f = LoansListFragment.newInstance(modes[position])
        fragments[position] = f
        return f
    }

    fun setLoans(loans: List<com.example.financetracker.models.Loan>) {
        for (f in fragments) {
            f?.setLoans(loans)
        }
    }
}
