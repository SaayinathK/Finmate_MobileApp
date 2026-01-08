package com.example.financetracker

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.financetracker.databinding.ActivitySettingsBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val exportRequestCode = 2001
    private val importRequestCode = 2002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NotificationHelper.createNotificationChannel(this)

        binding.backArrow.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        val currencyOptions = listOf("LKR", "USD", "EUR", "INR", "GBP", "AUD")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, currencyOptions)
        binding.currencyInput.setAdapter(adapter)
        binding.currencyInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.currencyInput.showDropDown()
        }
        binding.currencyInput.inputType = EditorInfo.TYPE_NULL

        loadSettings()

        binding.saveButton.setOnClickListener {
            val selectedCurrency = binding.currencyInput.text.toString()
            val prefs = getSharedPreferences("FinMatePrefs", MODE_PRIVATE)
            prefs.edit().putString("currency", selectedCurrency).apply()
            Toast.makeText(this, "Saved: $selectedCurrency", Toast.LENGTH_SHORT).show()
        }

        binding.exportButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_TITLE, "transactions.xlsx")
            }
            startActivityForResult(intent, exportRequestCode)
        }

        binding.importButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            }
            startActivityForResult(intent, importRequestCode)
        }

        binding.resetButton.setOnClickListener {
            showPasswordValidationDialog()
        }

        binding.logoutButton.setOnClickListener {
            showLogoutConfirmationDialog()
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> startActivity(Intent(this, HomeActivity::class.java))
                R.id.history -> startActivity(Intent(this, TransactionHistoryActivity::class.java))
                R.id.analysis -> startActivity(Intent(this, CategoryAnalysisActivity::class.java))
                R.id.setting -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return

        when (requestCode) {
            exportRequestCode -> if (resultCode == Activity.RESULT_OK) exportToExcel(uri)
            importRequestCode -> if (resultCode == Activity.RESULT_OK) importFromExcel(uri)
        }
    }

    private fun exportToExcel(uri: Uri) {
        try {
            val prefs = getSharedPreferences("FinMatePrefs", Context.MODE_PRIVATE)
            val json = prefs.getString("transactions", "[]")
            val type = object : TypeToken<List<Transaction>>() {}.type
            val transactions: List<Transaction> = Gson().fromJson(json, type)

            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Transactions")

            val headerRow = sheet.createRow(0)
            val headers = listOf("Title", "Amount", "Category", "Date", "Type")
            headers.forEachIndexed { index, title ->
                headerRow.createCell(index).setCellValue(title)
            }

            transactions.forEachIndexed { index, txn ->
                val row = sheet.createRow(index + 1)
                row.createCell(0).setCellValue(txn.title)
                row.createCell(1).setCellValue(txn.amount)
                row.createCell(2).setCellValue(txn.category)
                row.createCell(3).setCellValue(txn.date)
                row.createCell(4).setCellValue(txn.type)
            }

            contentResolver.openOutputStream(uri)?.use {
                workbook.write(it)
                workbook.close()
            }

            Toast.makeText(this, "Export successful!", Toast.LENGTH_SHORT).show()
            NotificationHelper.sendNotification(this, "Export Complete", "Transactions exported successfully.")

        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importFromExcel(uri: Uri) {
        try {
            val transactions = mutableListOf<Transaction>()
            contentResolver.openInputStream(uri)?.use { input ->
                val workbook = WorkbookFactory.create(input)
                val sheet = workbook.getSheetAt(0)

                for (i in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(i)
                    val txn = Transaction(
                        title = row.getCell(0).stringCellValue,
                        amount = row.getCell(1).numericCellValue,
                        category = row.getCell(2).stringCellValue,
                        date = row.getCell(3).stringCellValue,
                        type = row.getCell(4).stringCellValue
                    )
                    transactions.add(txn)
                }

                workbook.close()
                getSharedPreferences("FinMatePrefs", Context.MODE_PRIVATE).edit()
                    .putString("transactions", Gson().toJson(transactions)).apply()

                Toast.makeText(this, "Import successful!", Toast.LENGTH_SHORT).show()
                NotificationHelper.sendNotification(this, "Import Complete", "Transactions imported successfully.")
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showPasswordValidationDialog() {
        val input = EditText(this)
        input.hint = "Enter password"
        input.inputType = EditorInfo.TYPE_TEXT_VARIATION_PASSWORD

        AlertDialog.Builder(this)
            .setTitle("Password Required")
            .setMessage("Please enter your password to reset all data.")
            .setView(input)
            .setPositiveButton("Confirm") { _: DialogInterface, _: Int ->
                val enteredPassword = input.text.toString()
                val prefs = getSharedPreferences("FinMateUserData", Context.MODE_PRIVATE)
                val savedPassword = prefs.getString("password", "")

                if (enteredPassword == savedPassword) {
                    showResetConfirmationDialog()
                } else {
                    Toast.makeText(this, "Incorrect password. Reset cancelled.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showResetConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Reset")
            .setMessage("Are you sure you want to reset all data? This action cannot be undone.")
            .setPositiveButton("Yes") { _: DialogInterface, _: Int ->
                resetAllData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetAllData() {
        getSharedPreferences("FinMatePrefs", MODE_PRIVATE).edit().clear().apply()
        deleteFile("transactions.txt")

        getSharedPreferences("FinMateUserData", MODE_PRIVATE).edit().putBoolean("is_logged_in", false).apply()

        NotificationHelper.sendNotification(this, "Data Reset", "All app data has been erased.")
        Toast.makeText(this, "All data has been reset!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Logout")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Yes") { _: DialogInterface, _: Int ->
                logoutUser()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logoutUser() {
        getSharedPreferences("FinMateUserData", MODE_PRIVATE).edit().putBoolean("is_logged_in", false).apply()

        NotificationHelper.sendNotification(this, "Logout Successful", "You have been logged out from FinMate.")
        Toast.makeText(this, "You have been logged out!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("FinMatePrefs", MODE_PRIVATE)
        val currency = prefs.getString("currency", "LKR") ?: "LKR"
        binding.currencyInput.setText(currency, false)
    }
}
