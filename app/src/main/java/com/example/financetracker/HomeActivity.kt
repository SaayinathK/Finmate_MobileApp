package com.example.financetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.financetracker.databinding.ActivityHomeBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val gson = Gson()
    private val sharedKey = "transactions"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🔔 Setup notification channel
        NotificationHelper.createNotificationChannel(this)

        // ✅ Check if user is logged in
        val userPrefs = getSharedPreferences("FinMateUserData", MODE_PRIVATE)
        val isLoggedIn = userPrefs.getBoolean("is_logged_in", false)
        if (!isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // ✅ Load budget and currency from correct SharedPreferences
        val prefs = getSharedPreferences("FinMatePrefs", MODE_PRIVATE)
        val budgetLimit = prefs.getFloat("budget_limit", 5000f).toDouble()
        val currency = prefs.getString("currency", "LKR") ?: "LKR"

        // 💼 Load saved transactions
        val transactions = loadTransactions()

        // 📊 Calculate totals
        val income = transactions.filter { it.type == "Income" }.sumOf { it.amount }
        val expense = transactions.filter { it.type == "Expense" }.sumOf { it.amount }.let { kotlin.math.abs(it) }
        val balance = income - expense
        val percentageUsed = if (budgetLimit > 0) ((expense / budgetLimit) * 100).toInt() else 0

        // 🖥 Update UI
        binding.balanceAmount.text = "$currency %.2f".format(balance)
        binding.incomeAmount.text = "$currency %.2f".format(income)
        binding.expenseAmount.text = "$currency %.2f".format(expense)

        // ➕ Optionally show budget limit somewhere in your layout
        // You must add a TextView like: android:id="@+id/budgetLimitText" in your XML
        binding.budgetLimitText?.text = "Budget Limit: $currency %.2f".format(budgetLimit)

        binding.budgetProgressBar.progress = percentageUsed
        binding.budgetWarning.text = when {
            percentageUsed >= 100 -> "⚠ Budget Exceeded"
            percentageUsed >= 80 -> "⚠ Nearing Budget Limit"
            else -> "Good Standing"
        }

        if (expense > budgetLimit) {
            NotificationHelper.showBudgetExceededNotification(this)
        }

        // 🚀 Navigation Buttons
        binding.addTransactionButton.setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }
        binding.transactionHistoryButton.setOnClickListener {
            startActivity(Intent(this, TransactionHistoryActivity::class.java))
        }
        binding.categoryAnalysisButton.setOnClickListener {
            startActivity(Intent(this, CategoryAnalysisActivity::class.java))
        }
        binding.budgetSetupButton.setOnClickListener {
            startActivity(Intent(this, BudgetSetupActivity::class.java))
        }
        binding.exportImportButton.setOnClickListener {
            startActivity(Intent(this, ExportImportActivity::class.java))
        }

        // 🔄 Refresh
        binding.refreshButton.setOnClickListener {
            recreate()
        }

        // 🧭 Bottom Navigation
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> { startActivity(Intent(this, HomeActivity::class.java)); true }
                R.id.history -> { startActivity(Intent(this, TransactionHistoryActivity::class.java)); true }
                R.id.analysis -> { startActivity(Intent(this, CategoryAnalysisActivity::class.java)); true }
                R.id.setting -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
                else -> false
            }
        }
    }

    private fun loadTransactions(): List<Transaction> {
        val prefs = getSharedPreferences("FinMatePrefs", MODE_PRIVATE)
        val json = prefs.getString(sharedKey, "[]")
        val type = object : TypeToken<List<Transaction>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
}
