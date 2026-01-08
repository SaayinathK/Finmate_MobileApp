package com.example.financetracker.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.R
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.databinding.ActivityHomeBinding
import com.example.financetracker.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*


class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var db: AppDatabase

    private var selectedMonth = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date())
    private var selectedYear = Calendar.getInstance().get(Calendar.YEAR).toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)
        NotificationHelper.createNotificationChannel(this)

        binding.btnSelectDateRange.setOnClickListener {
            showMonthYearDialog()
        }

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

        // Setup header (title only, hide back button for Home)
        val headerTitle = findViewById<android.widget.TextView>(com.example.financetracker.R.id.headerTitle)
        val backArrow = findViewById<android.widget.ImageView>(com.example.financetracker.R.id.backArrow)
        headerTitle?.text = "Dashboard"
        backArrow?.visibility = android.view.View.GONE

        // Attach centralized bottom navigation behavior when present
        com.example.financetracker.ui.BottomNavHelper.attach(this)
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    private fun showMonthYearDialog() {
        val dialogLayout = LayoutInflater.from(this).inflate(R.layout.dialog_month_year_filter, null)
        val monthSpinner = dialogLayout.findViewById<Spinner>(R.id.spinnerMonth)
        val yearSpinner = dialogLayout.findViewById<Spinner>(R.id.spinnerYear)

        lifecycleScope.launch {
            val transactions = withContext(Dispatchers.IO) {
                db.transactionDao().getAllTransactionsRaw()
            }

            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val keys = transactions.mapNotNull {
                try {
                    val date = sdf.parse(it.date)
                    val month = SimpleDateFormat("MMMM", Locale.getDefault()).format(date!!)
                    val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(date)
                    year to month
                } catch (e: Exception) { null }
            }.distinct()

            val monthList = keys.map { it.second }.distinct().sortedBy { month ->
                SimpleDateFormat("MMMM", Locale.ENGLISH).parse(month)?.month
            }
            val yearList = keys.map { it.first }.distinct().sortedDescending()

            val monthAdapter = ArrayAdapter(this@HomeActivity, android.R.layout.simple_spinner_item, monthList)
            val yearAdapter = ArrayAdapter(this@HomeActivity, android.R.layout.simple_spinner_item, yearList)

            monthSpinner.adapter = monthAdapter
            yearSpinner.adapter = yearAdapter

            monthSpinner.setSelection(monthList.indexOf(selectedMonth))
            yearSpinner.setSelection(yearList.indexOf(selectedYear))

            AlertDialog.Builder(this@HomeActivity)
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
    }

    private fun refreshUI() {
        lifecycleScope.launch {
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val monthFormatter = SimpleDateFormat("MMMM", Locale.getDefault())
            val yearFormatter = SimpleDateFormat("yyyy", Locale.getDefault())

            val transactions = withContext(Dispatchers.IO) {
                db.transactionDao().getAllTransactionsRaw()
            }.filter {
                try {
                    val date = sdf.parse(it.date) ?: return@filter false
                    val txnMonth = monthFormatter.format(date)
                    val txnYear = yearFormatter.format(date)
                    txnMonth == selectedMonth && txnYear == selectedYear
                } catch (e: Exception) {
                    false
                }
            }

            val income = transactions.filter { it.type == "Income" }.sumOf { it.amount }
            val expense = transactions.filter { it.type == "Expense" }.sumOf { it.amount }.let { kotlin.math.abs(it) }
            val balance = income - expense

            val budgetKey = "${selectedYear}_${selectedMonth}"
            val budget = withContext(Dispatchers.IO) { db.budgetDao().getBudgetByKey(budgetKey) }
            val currency = budget?.currency ?: "LKR"
            val budgetLimit = budget?.amount ?: 0.0
            val percentageUsed = if (budgetLimit > 0) ((expense / budgetLimit) * 100).toInt() else 0

            binding.textSelectedMonthYear.text = "$selectedYear $selectedMonth"
            binding.balanceAmount.text = "$currency %.2f".format(balance)
            binding.incomeAmount.text = "$currency %.2f".format(income)
            binding.expenseAmount.text = "$currency %.2f".format(expense)
            binding.budgetProgressBar.progress = percentageUsed
            binding.budgetLimitText.text = "Budget Limit: $currency %.2f".format(budgetLimit)

            when {
                percentageUsed >= 100 -> {
                    val red = ContextCompat.getColor(this@HomeActivity, android.R.color.holo_red_dark)
                    binding.budgetWarning.text = "⚠ Budget Exceeded"
                    binding.budgetWarning.setTextColor(red)
                    binding.budgetProgressBar.setIndicatorColor(red)
                }
                percentageUsed >= 80 -> {
                    val orange = ContextCompat.getColor(this@HomeActivity, android.R.color.holo_orange_dark)
                    binding.budgetWarning.text = "⚠ Nearing Budget Limit"
                    binding.budgetWarning.setTextColor(orange)
                    binding.budgetProgressBar.setIndicatorColor(orange)
                }
                else -> {
                    val green = ContextCompat.getColor(this@HomeActivity, android.R.color.holo_green_dark)
                    binding.budgetWarning.text = "Good Standing"
                    binding.budgetWarning.setTextColor(green)
                    binding.budgetProgressBar.setIndicatorColor(green)
                }
            }

            if (expense > budgetLimit) {
                NotificationHelper.showBudgetExceededNotification(this@HomeActivity)
            }
        }
    }
}
