// activities/BudgetSetupActivity.kt
package com.example.financetracker.activities

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.R
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.databinding.ActivityBudgetSetupBinding
import com.example.financetracker.models.Budget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*


class BudgetSetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBudgetSetupBinding
    private val allMonths = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    private val allCurrencies = listOf("LKR", "USD", "EUR", "INR", "GBP", "AUD")
    private var selectedMonth = ""
    private var selectedYear = ""
    private var selectedCurrency = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBudgetSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set header title and back button
        val headerTitle = findViewById<android.widget.TextView>(R.id.headerTitle)
        headerTitle?.text = "Set Monthly Budget"
        val backArrow = findViewById<android.view.View>(R.id.backArrow)
        backArrow?.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }

        setupDropdowns()
        setupListeners()
        com.example.financetracker.ui.BottomNavHelper.attach(this)
    }

    private fun setupDropdowns() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = (currentYear..currentYear + 5).map { it.toString() }

        // Setup Month Dropdown with AutoCompleteTextView
        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, allMonths)
        binding.monthInput.setAdapter(monthAdapter)
        binding.monthInput.threshold = 0 // Show dropdown immediately
        binding.monthInput.setOnClickListener { binding.monthInput.showDropDown() }

        // Setup Year Dropdown with AutoCompleteTextView
        val yearAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, years)
        binding.yearInput.setAdapter(yearAdapter)
        binding.yearInput.threshold = 0
        binding.yearInput.setOnClickListener { binding.yearInput.showDropDown() }

        // Setup Currency Dropdown with AutoCompleteTextView
        val currencyAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, allCurrencies)
        binding.currencyInput.setAdapter(currencyAdapter)
        binding.currencyInput.threshold = 0
        binding.currencyInput.setOnClickListener { binding.currencyInput.showDropDown() }
    }

    private fun setupListeners() {
        binding.saveButton.setOnClickListener { saveBudget() }
        // Back button handled by header include

        // Month, Year, and Currency Selection
        binding.monthInput.setOnItemClickListener { _, _, position, _ ->
            selectedMonth = allMonths[position]
            loadBudgetIfSelected()
        }

        binding.yearInput.setOnItemClickListener { _, _, position, _ ->
            selectedYear = binding.yearInput.adapter.getItem(position).toString()
            loadBudgetIfSelected()
        }

        binding.currencyInput.setOnItemClickListener { _, _, position, _ ->
            selectedCurrency = allCurrencies[position]
        }
    }

    // Bottom navigation handled centrally via BottomNavHelper

    private fun loadBudgetIfSelected() {
        if (selectedMonth.isNotBlank() && selectedYear.isNotBlank()) {
            loadBudget()
        }
    }

    private fun loadBudget() {
        val key = "${selectedYear}_$selectedMonth"
        lifecycleScope.launch {
            val budget = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(this@BudgetSetupActivity).budgetDao().getBudgetByKey(key)
            }

            // Load budget values if exists
            binding.budgetInput.setText(budget?.amount?.toString() ?: "")
            binding.currencyInput.setText(budget?.currency ?: "", false)
            calculateTotalSpending()
        }
    }

    private fun calculateTotalSpending() {
        if (selectedMonth.isBlank() || selectedYear.isBlank()) return

        val key = "${selectedYear}_$selectedMonth"
        lifecycleScope.launch {
            val transactions = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(this@BudgetSetupActivity).transactionDao().getAllTransactionsRaw()
            }.filter {
                try {
                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    val date = sdf.parse(it.date)
                    val txnMonth = SimpleDateFormat("MMMM", Locale.getDefault()).format(date!!)
                    val txnYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(date)
                    txnMonth == selectedMonth && txnYear == selectedYear
                } catch (e: Exception) {
                    false
                }
            }

            val totalExpense = transactions
                .filter { it.type.equals("Expense", true) }
                .sumOf { kotlin.math.abs(it.amount) }

            binding.currentMonthStatus.text =
                "Total Spending in $selectedMonth $selectedYear: LKR %.2f".format(totalExpense)
        }
    }

    private fun saveBudget() {
        val budgetText = binding.budgetInput.text.toString()
        val currency = binding.currencyInput.text.toString()

        if (selectedMonth.isBlank() || selectedYear.isBlank() || currency.isBlank()) {
            Toast.makeText(this, "Please select Month, Year, and Currency", Toast.LENGTH_SHORT).show()
            return
        }

        if (budgetText.isBlank()) {
            Toast.makeText(this, "Please enter a budget amount", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = budgetText.toDoubleOrNull()
        if (amount == null) {
            Toast.makeText(this, "Invalid budget amount", Toast.LENGTH_SHORT).show()
            return
        }

        val key = "${selectedYear}_$selectedMonth"
        val budget = Budget(key, amount, currency)

        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getDatabase(this@BudgetSetupActivity).budgetDao().insertBudget(budget)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@BudgetSetupActivity,
                    "Budget saved for $selectedMonth $selectedYear!",
                    Toast.LENGTH_SHORT
                ).show()
                startActivity(Intent(this@BudgetSetupActivity, HomeActivity::class.java))
                finish()
            }
        }
    }
}
