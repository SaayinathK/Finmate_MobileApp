package com.example.financetracker

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.financetracker.databinding.ActivityCategoryAnalysisBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.util.Log


class CategoryAnalysisActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategoryAnalysisBinding
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoryAnalysisBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Back navigation
        binding.backArrow.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }

        // Setup visuals
        setupPieChart()
        setupBarChart()
        displaySummary()

        // Bottom navigation
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> { startActivity(Intent(this, HomeActivity::class.java)); true }
                R.id.history -> { startActivity(Intent(this, TransactionHistoryActivity::class.java)); true }
                R.id.analysis -> true
                R.id.setting -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
                else -> false
            }
        }
    }

    private fun setupPieChart() {
        val transactions = loadTransactions()

        val expenseMap = transactions
            .filter { it.type == "Expense" }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { kotlin.math.abs(it.amount) } }

        if (expenseMap.isEmpty()) {
            binding.pieChart.clear()
            binding.pieChart.setNoDataText("No expense data to display.")
            binding.pieChart.invalidate()
            return
        }

        val entries = expenseMap.map { (category, total) ->
            PieEntry(total.toFloat(), category)
        }

        val colors = listOf("#FFA726", "#29B6F6", "#66BB6A", "#AB47BC", "#EF5350", "#FF7043")
            .map { Color.parseColor(it) }

        val dataSet = PieDataSet(entries, "Expenses by Category").apply {
            this.colors = colors
            sliceSpace = 2f
            valueTextSize = 14f
            valueTextColor = Color.WHITE
        }

        binding.pieChart.apply {
            data = PieData(dataSet)
            description.isEnabled = false
            isRotationEnabled = true
            setUsePercentValues(true)
            setEntryLabelColor(Color.BLACK)
            setEntryLabelTextSize(12f)
            animateY(1000)
            invalidate()
        }
    }

    private fun setupBarChart() {
        val transactions = loadTransactions()

        val incomeMap = transactions
            .filter { it.type == "Income" }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { tx -> kotlin.math.abs(tx.amount) } }

        val expenseMap = transactions
            .filter { it.type == "Expense" }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { tx -> kotlin.math.abs(tx.amount) } }

        val allCategories = (incomeMap.keys + expenseMap.keys).distinct()
        if (allCategories.isEmpty()) {
            binding.barChart.clear()
            binding.barChart.setNoDataText("No income/expense data to display.")
            binding.barChart.invalidate()
            return
        }

        val incomeEntries = mutableListOf<BarEntry>()
        val expenseEntries = mutableListOf<BarEntry>()
        val labels = allCategories.toList()

        labels.forEachIndexed { index, category ->
            incomeEntries.add(BarEntry(index.toFloat(), incomeMap[category]?.toFloat() ?: 0f))
            expenseEntries.add(BarEntry(index.toFloat(), expenseMap[category]?.toFloat() ?: 0f))
        }

        val incomeDataSet = BarDataSet(incomeEntries, "Income").apply {
            color = Color.parseColor("#66BB6A") // Green
            valueTextColor = Color.BLACK
            valueTextSize = 12f
        }

        val expenseDataSet = BarDataSet(expenseEntries, "Expense").apply {
            color = Color.parseColor("#EF5350") // Red
            valueTextColor = Color.BLACK
            valueTextSize = 12f
        }

        val barData = BarData(incomeDataSet, expenseDataSet).apply {
            barWidth = 0.4f
        }

        val groupSpace = 0.2f
        val barSpace = 0f

        binding.barChart.apply {
            data = barData
            description.isEnabled = false
            setFitBars(true)
            animateY(1000)
            setDrawValueAboveBar(true)
            legend.isEnabled = true
            axisRight.isEnabled = false

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                isGranularityEnabled = true
                setDrawGridLines(false)
                textSize = 12f
                axisMinimum = -0.5f
                axisMaximum = labels.size.toFloat()
            }

            axisLeft.apply {
                axisMinimum = 0f
                textSize = 12f
            }

            barData.groupBars(0f, groupSpace, barSpace)
            invalidate()
        }
    }

    private fun displaySummary() {
        val transactions = loadTransactions()

        // Normalize values to positive to avoid double-negatives
        val totalIncome = transactions
            .filter { it.type.equals("Income", ignoreCase = true) }
            .sumOf { kotlin.math.abs(it.amount) }

        val totalExpense = transactions
            .filter { it.type.equals("Expense", ignoreCase = true) }
            .sumOf { kotlin.math.abs(it.amount) }

        val currency = getSharedPreferences("FinMatePrefs", Context.MODE_PRIVATE)
            .getString("currency", "LKR") ?: "LKR"

        val balance = totalIncome - totalExpense

        // Display with formatting
        binding.incomeAmount.text = "$currency %.2f".format(totalIncome)
        binding.expenseAmount.text = "$currency %.2f".format(totalExpense)
        binding.balanceAmount.text = "$currency %.2f".format(balance)

        // Optional: Debug log
        Log.d("Summary", "Income: $totalIncome, Expense: $totalExpense, Balance: $balance")
    }


    private fun loadTransactions(): List<Transaction> {
        val prefs = getSharedPreferences("FinMatePrefs", Context.MODE_PRIVATE)
        val json = prefs.getString("transactions", "[]")
        val type = object : TypeToken<List<Transaction>>() {}.type
        return gson.fromJson(json, type)
    }
}
