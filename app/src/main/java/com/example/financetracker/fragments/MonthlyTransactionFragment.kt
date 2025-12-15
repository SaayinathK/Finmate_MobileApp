package com.example.financetracker.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.financetracker.adapters.MonthlyTransactionAdapter
import com.example.financetracker.databinding.FragmentMonthlyTransactionBinding
import com.example.financetracker.models.MonthlyTransactionItem
import com.example.financetracker.models.Transaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MonthlyTransactionFragment : Fragment() {

    private var _binding: FragmentMonthlyTransactionBinding? = null
    private val binding get() = _binding!!

    private lateinit var month: String
    private lateinit var transactions: List<Transaction>

    companion object {
        fun newInstance(month: String, transactions: List<Transaction>): MonthlyTransactionFragment {
            val fragment = MonthlyTransactionFragment()
            val bundle = Bundle().apply {
                putString("month", month)
                putString("transactions", Gson().toJson(transactions))
            }
            fragment.arguments = bundle
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            month = it.getString("month") ?: "Unknown"
            val json = it.getString("transactions") ?: "[]"
            val type = object : TypeToken<List<Transaction>>() {}.type
            transactions = Gson().fromJson(json, type)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMonthlyTransactionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val adapterItems = transactions.map { MonthlyTransactionItem.TransactionItem(it) }
        binding.recyclerView.adapter = MonthlyTransactionAdapter(adapterItems, {}, {})
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
