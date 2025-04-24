package com.example.financetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.financetracker.databinding.ActivityBudgetSetupBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class BudgetSetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBudgetSetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBudgetSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        // Back arrow click → HomeActivity
        binding.backArrow.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }

        displayTotalExpense() // ✅ Call total expense function on load

        binding.saveButton.setOnClickListener {
            val budgetText = binding.budgetInput.text.toString()
            val currency = binding.currencyInput.text.toString()

            if (budgetText.isEmpty() || currency.isEmpty()) {
                Toast.makeText(this, "Please enter budget and currency", Toast.LENGTH_SHORT).show()
            } else {
                val budget = budgetText.toDoubleOrNull()

                if (budget == null) {
                    Toast.makeText(this, "Invalid budget amount", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val prefs = getSharedPreferences("FinMatePrefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putFloat("budget_limit", budget.toFloat())
                    .putString("currency", currency)
                    .apply()

                Toast.makeText(this, "Budget saved: $currency $budget", Toast.LENGTH_SHORT).show()
                finish() // Close activity after saving
            }
        }
    }

    private fun displayTotalExpense() {
        val prefs = getSharedPreferences("FinMatePrefs", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("transactions", "[]")
        val type = object : TypeToken<List<Transaction>>() {}.type
        val transactions = gson.fromJson<List<Transaction>>(json, type) ?: emptyList()

        val totalExpense = transactions
            .filter { it.type == "Expense" }
            .sumOf { kotlin.math.abs(it.amount) }

        val currency = prefs.getString("currency", "LKR") ?: "LKR"
        binding.currentMonthStatus.text = "Total Spending: $currency %.2f".format(totalExpense)
    }
}
