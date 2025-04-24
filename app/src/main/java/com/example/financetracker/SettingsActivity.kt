package com.example.financetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.financetracker.databinding.ActivitySettingsBinding
import java.io.*

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val currencies = listOf("USD", "EUR", "LKR", "INR", "GBP")
    private val fileName = "transactions.txt"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Back arrow click → HomeActivity
        binding.backArrow.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }

        // Set up the currency spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, currencies)
        binding.currencySpinner.setAdapter(adapter)

        // Load the saved settings (currency and notifications status)
        loadSettings()

        // Save settings when the user presses the Save button
        binding.saveButton.setOnClickListener {
            val selectedCurrency = binding.currencySpinner.text.toString()
            val notificationsEnabled = binding.notificationsSwitch.isChecked

            // Save preferences
            val prefs = getSharedPreferences("FinMatePrefs", MODE_PRIVATE)
            val editor = prefs.edit()
            editor.putString("currency", selectedCurrency)
            editor.putBoolean("notifications_enabled", notificationsEnabled)
            editor.apply()

            Toast.makeText(this, "Saved: $selectedCurrency | Notifications: $notificationsEnabled", Toast.LENGTH_SHORT).show()
        }

        // EXPORT data
        binding.exportButton.setOnClickListener {
            val transactions = loadTransactions()  // Assuming you have a method to get transactions
            val transactionStrings = transactions.map {
                "Title: ${it.title}, Amount: ${it.amount}, Category: ${it.category}, Date: ${it.date}, Type: ${it.type}"
            }
            exportData(transactionStrings)
        }

        // IMPORT data
        binding.importButton.setOnClickListener {
            val data = importData()
            if (data.isNotEmpty()) {
                Toast.makeText(this, "Imported: ${data.size} items", Toast.LENGTH_SHORT).show()
                data.forEach { println("Imported: $it") }
            } else {
                Toast.makeText(this, "No data to import!", Toast.LENGTH_SHORT).show()
            }
        }

        // RESET data
        binding.resetButton.setOnClickListener {
            resetAllData()
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> {
                    startActivity(Intent(this, HomeActivity::class.java))
                    true
                }
                R.id.history -> {
                    startActivity(Intent(this, TransactionHistoryActivity::class.java))
                    true
                }
                R.id.analysis -> {
                    startActivity(Intent(this, CategoryAnalysisActivity::class.java))
                    true
                }
                R.id.setting -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("FinMatePrefs", MODE_PRIVATE)
        val currency = prefs.getString("currency", "LKR") ?: "LKR"
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)

        binding.currencySpinner.setText(currency, false)
        binding.notificationsSwitch.isChecked = notificationsEnabled
    }

    private fun exportData(data: List<String>) {
        try {
            openFileOutput(fileName, Context.MODE_PRIVATE).use { output ->
                data.forEach { output.write((it + "\n").toByteArray()) }
            }
            Toast.makeText(this, "Data exported to $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Export failed!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importData(): List<String> {
        val result = mutableListOf<String>()
        try {
            openFileInput(fileName).bufferedReader().useLines { lines ->
                lines.forEach { result.add(it) }
            }
        } catch (e: FileNotFoundException) {
            Toast.makeText(this, "No file found to import!", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Import failed!", Toast.LENGTH_SHORT).show()
        }
        return result
    }

    private fun resetAllData() {
        val prefs = getSharedPreferences("FinMatePrefs", MODE_PRIVATE)
        val editor = prefs.edit()
        editor.clear()  // Clear all preferences (currency, notifications, etc.)
        editor.apply()

        // Optionally, reset transactions data as well
        deleteFile(fileName)

        Toast.makeText(this, "All data has been reset!", Toast.LENGTH_SHORT).show()

        // Optionally, log the user out
        val userPrefs = getSharedPreferences("FinMateUserData", Context.MODE_PRIVATE)
        val userEditor = userPrefs.edit()
        userEditor.putBoolean("is_logged_in", false)  // Set logged-in status to false
        userEditor.apply()

        // Redirect to login screen
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    // Now this method loads real transaction data
    private fun loadTransactions(): List<Transaction> {
        // Example data - replace with your actual transaction loading logic
        return listOf(
            Transaction("Coffee", 300.0, "Food", "2024-04-10", "Expense"),
            Transaction("Bus Fare", 150.0, "Transport", "2024-04-11", "Expense"),
            Transaction("Salary", 5000.0, "Income", "2024-04-01", "Income")
        )
    }
}
