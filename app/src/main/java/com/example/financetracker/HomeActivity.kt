package com.example.financetracker

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

        // 🔔 Create Notification Channel
        NotificationHelper.createNotificationChannel(this)

        // ✅ Check if user is logged in
        val userPrefs = getSharedPreferences("FinMateUserData", MODE_PRIVATE)
        val isLoggedIn = userPrefs.getBoolean("is_logged_in", false)
        if (!isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // 🖥 Initial UI Update
        refreshUI()

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


        // 🧭 Bottom Navigation
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> {
                    startActivity(Intent(this, HomeActivity::class.java)); true
                }
                R.id.history -> {
                    startActivity(Intent(this, TransactionHistoryActivity::class.java)); true
                }
                R.id.analysis -> {
                    startActivity(Intent(this, CategoryAnalysisActivity::class.java)); true
                }
                R.id.setting -> {
                    startActivity(Intent(this, SettingsActivity::class.java)); true
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUI() // 🔁 Auto-refresh on resume
    }

    private fun loadTransactions(): List<Transaction> {
        val prefs = getSharedPreferences("FinMatePrefs", MODE_PRIVATE)
        val json = prefs.getString(sharedKey, "[]")
        val type = object : TypeToken<List<Transaction>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun refreshUI() {
        val prefs = getSharedPreferences("FinMatePrefs", MODE_PRIVATE)
        val budgetLimit = prefs.getFloat("budget_limit", 5000f).toDouble()
        val currency = prefs.getString("currency", "LKR") ?: "LKR"
        val transactions = loadTransactions()

        val income = transactions.filter { it.type == "Income" }.sumOf { it.amount }
        val expense = transactions.filter { it.type == "Expense" }.sumOf { it.amount }.let { kotlin.math.abs(it) }
        val balance = income - expense
        val percentageUsed = if (budgetLimit > 0) ((expense / budgetLimit) * 100).toInt() else 0

        binding.balanceAmount.text = "$currency %.2f".format(balance)
        binding.incomeAmount.text = "$currency %.2f".format(income)
        binding.expenseAmount.text = "$currency %.2f".format(expense)
        binding.budgetProgressBar.progress = percentageUsed
        binding.budgetLimitText.text = "Budget Limit: $currency %.2f".format(budgetLimit)

        // Set warning text and color with matching line
        when {
            percentageUsed >= 100 -> {
                val red = ContextCompat.getColor(this, android.R.color.holo_red_dark)
                binding.budgetWarning.text = "⚠ Budget Exceeded"
                binding.budgetWarning.setTextColor(red)
                binding.budgetProgressBar.setIndicatorColor(red)
            }
            percentageUsed >= 80 -> {
                val orange = ContextCompat.getColor(this, android.R.color.holo_orange_dark)
                binding.budgetWarning.text = "⚠ Nearing Budget Limit"
                binding.budgetWarning.setTextColor(orange)
                binding.budgetProgressBar.setIndicatorColor(orange)
            }
            else -> {
                val green = ContextCompat.getColor(this, android.R.color.holo_green_dark)
                binding.budgetWarning.text = "Good Standing"
                binding.budgetWarning.setTextColor(green)
                binding.budgetProgressBar.setIndicatorColor(green)
            }
        }

        // 🔔 Notify if exceeded
        if (expense > budgetLimit) {
            NotificationHelper.showBudgetExceededNotification(this)
        }
    }
}
