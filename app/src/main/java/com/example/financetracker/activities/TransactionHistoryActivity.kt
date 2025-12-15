package com.example.financetracker.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.financetracker.R
import com.example.financetracker.adapters.MonthlyTransactionAdapter
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.databinding.ActivityTransactionHistoryBinding
import com.example.financetracker.models.MonthlyTransactionItem
import com.example.financetracker.models.Transaction
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class TransactionHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransactionHistoryBinding
    private lateinit var adapter: MonthlyTransactionAdapter
    private val groupedItems = mutableListOf<MonthlyTransactionItem>()

    private val monthList = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    private var selectedYear = Calendar.getInstance().get(Calendar.YEAR).toString()
    private var selectedMonth = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date())
    private var selectedTab = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backArrow.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }

        binding.fabAddTransaction.setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }

        binding.btnSelectDateRange.setOnClickListener {
            showMonthYearDialog()
        }

        setupRecyclerView()
        setupTabFilter()
        // Use centralized bottom navigation
        com.example.financetracker.ui.BottomNavHelper.attach(this)
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    private fun refreshUI() {
        lifecycleScope.launch {
            val transactions = loadTransactionsFromDB()
            groupTransactions(transactions)
            updateSelectedDateDisplay()
        }
    }

    private suspend fun loadTransactionsFromDB(): List<Transaction> {
        val dao = AppDatabase.getDatabase(applicationContext).transactionDao()
        return withContext(Dispatchers.IO) {
            dao.getAllTransactionsRaw()
        }
    }

    private fun updateSelectedDateDisplay() {
        binding.textSelectedMonthYear.text = "$selectedMonth $selectedYear"
    }

    private fun showMonthYearDialog() {
        val dialogLayout = LayoutInflater.from(this).inflate(R.layout.dialog_month_year_filter, null)
        val monthSpinner = dialogLayout.findViewById<Spinner>(R.id.spinnerMonth)
        val yearSpinner = dialogLayout.findViewById<Spinner>(R.id.spinnerYear)

        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, monthList)
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        monthSpinner.adapter = monthAdapter
        monthSpinner.setSelection(monthList.indexOf(selectedMonth))

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val yearList = (currentYear downTo 2015).map { it.toString() }
        val yearAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, yearList)
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        yearSpinner.adapter = yearAdapter
        yearSpinner.setSelection(yearList.indexOf(selectedYear))

        AlertDialog.Builder(this)
            .setTitle("Filter by Month & Year")
            .setView(dialogLayout)
            .setPositiveButton("Apply") { _, _ ->
                selectedMonth = monthSpinner.selectedItem.toString()
                selectedYear = yearSpinner.selectedItem.toString()
                refreshUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupTabFilter() {
        binding.filterTabLayout.apply {
            addTab(newTab().setText("All"))
            addTab(newTab().setText("Income"))
            addTab(newTab().setText("Expenses"))

            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    selectedTab = tab.text.toString()
                    refreshUI()
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            })
        }
    }

    private fun setupRecyclerView() {
        adapter = MonthlyTransactionAdapter(
            items = groupedItems,
            onDelete = { transaction ->
                lifecycleScope.launch {
                    AppDatabase.getDatabase(applicationContext).transactionDao().deleteTransaction(transaction)
                    refreshUI()
                    Toast.makeText(this@TransactionHistoryActivity, "Deleted", Toast.LENGTH_SHORT).show()
                }
            },
            onEdit = { transaction ->
                val intent = Intent(this, AddTransactionActivity::class.java).apply {
                    putExtra("editMode", true)
                    putExtra("transactionId", transaction.id)
                }
                startActivity(intent)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun groupTransactions(transactions: List<Transaction>) {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val monthFormatter = SimpleDateFormat("MMMM", Locale.getDefault())
        val yearFormatter = SimpleDateFormat("yyyy", Locale.getDefault())

        groupedItems.clear()

        val filtered = transactions.filter {
            try {
                val date = sdf.parse(it.date)!!
                monthFormatter.format(date) == selectedMonth && yearFormatter.format(date) == selectedYear
            } catch (e: Exception) {
                false
            }
        }.filter {
            when (selectedTab) {
                "Income" -> it.type.equals("income", true)
                "Expenses" -> it.type.equals("expense", true)
                else -> true
            }
        }.sortedByDescending {
            try {
                sdf.parse(it.date)
            } catch (e: Exception) {
                Date(0)
            }
        }

        if (filtered.isNotEmpty()) {
            groupedItems.add(MonthlyTransactionItem.Header("$selectedTab - $selectedMonth $selectedYear"))
            groupedItems.addAll(filtered.map { MonthlyTransactionItem.TransactionItem(it) })
        }

        adapter.updateList(groupedItems)
    }

    // Bottom navigation handled centrally via BottomNavHelper
}
