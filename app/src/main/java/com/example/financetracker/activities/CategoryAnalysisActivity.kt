package com.example.financetracker.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.R
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.databinding.ActivityCategoryAnalysisBinding
import com.example.financetracker.models.Transaction
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class CategoryAnalysisActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategoryAnalysisBinding
    private lateinit var db: AppDatabase

    private var selectedMonth = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date())
    private var selectedYear = Calendar.getInstance().get(Calendar.YEAR).toString()
    private val monthList = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoryAnalysisBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)
        refreshAll()

        binding.btnSelectDateRange.setOnClickListener {
            showMonthYearDialog()
        }

        binding.backArrow.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }

        // Centralized bottom navigation
        com.example.financetracker.ui.BottomNavHelper.attach(this)
    }

    private fun showMonthYearDialog() {
        val dialogLayout = LayoutInflater.from(this).inflate(R.layout.dialog_month_year_filter, null)
        val monthSpinner = dialogLayout.findViewById<Spinner>(R.id.spinnerMonth)
        val yearSpinner = dialogLayout.findViewById<Spinner>(R.id.spinnerYear)

        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, monthList)
        monthSpinner.adapter = monthAdapter
        monthSpinner.setSelection(monthList.indexOf(selectedMonth))

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val yearList = (currentYear downTo currentYear - 10).map { it.toString() }
        val yearAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, yearList)
        yearSpinner.adapter = yearAdapter
        yearSpinner.setSelection(yearList.indexOf(selectedYear))

        AlertDialog.Builder(this)
            .setTitle("Filter by Month & Year")
            .setView(dialogLayout)
            .setPositiveButton("Apply") { _, _ ->
                selectedMonth = monthSpinner.selectedItem.toString()
                selectedYear = yearSpinner.selectedItem.toString()
                refreshAll()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshAll() {
        lifecycleScope.launch {
            val transactions = withContext(Dispatchers.IO) {
                db.transactionDao().getAllTransactionsRaw()
            }
            val currency = getCurrencyForSelectedPeriod()
            updatePieChart(transactions)
            updateBarChart(transactions)
            updateLineChart(transactions)
            updateSummary(transactions, currency)
            binding.textSelectedMonthYear.text = "$selectedMonth $selectedYear"
        }
    }

    private suspend fun getCurrencyForSelectedPeriod(): String {
        val budgetKey = "${selectedYear}_${selectedMonth}"
        return withContext(Dispatchers.IO) {
            db.budgetDao().getCurrencyByBudgetKey(budgetKey) ?: "LKR"
        }
    }

    private fun updateSummary(transactions: List<Transaction>, currency: String) {
        val filtered = transactions.filter {
            try {
                val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).parse(it.date)
                val cal = Calendar.getInstance().apply { time = date!! }
                cal.get(Calendar.MONTH).let { monthList[it] } == selectedMonth &&
                        cal.get(Calendar.YEAR).toString() == selectedYear
            } catch (_: Exception) { false }
        }

        val totalIncome = filtered.filter { it.type == "Income" }.sumOf { kotlin.math.abs(it.amount) }
        val totalExpense = filtered.filter { it.type == "Expense" }.sumOf { kotlin.math.abs(it.amount) }
        val balance = totalIncome - totalExpense

        binding.incomeAmount.text = "$currency %.2f".format(totalIncome)
        binding.expenseAmount.text = "$currency %.2f".format(totalExpense)
        binding.balanceAmount.text = "$currency %.2f".format(balance)
    }

    // 👇 ALL THESE METHODS REMAIN SAME AS YOUR VERSION (NO CHANGES NEEDED)
    // updatePieChart(), updateBarChart(), updateLineChart(), updateSummary()

    private fun updatePieChart(transactions: List<Transaction>) {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val monthFormatter = SimpleDateFormat("MMMM", Locale.getDefault())
        val yearFormatter = SimpleDateFormat("yyyy", Locale.getDefault())

        val expenseMap = transactions.filter {
            try {
                val date = sdf.parse(it.date)
                it.type == "Expense" && monthFormatter.format(date!!) == selectedMonth && yearFormatter.format(date) == selectedYear
            } catch (_: Exception) { false }
        }.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { kotlin.math.abs(it.amount) } }

        if (expenseMap.isEmpty()) {
            binding.pieChart.clear()
            binding.pieChart.setNoDataText("No expense data for $selectedMonth $selectedYear.")
            binding.pieChart.invalidate()
            return
        }

        val entries = expenseMap.map { (category, total) -> PieEntry(total.toFloat(), category) }
        val colors = listOf("#FFA726", "#29B6F6", "#66BB6A", "#AB47BC", "#EF5350", "#FF7043").map { Color.parseColor(it) }

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

    private fun updateBarChart(transactions: List<Transaction>) {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val monthFormatter = SimpleDateFormat("MMMM", Locale.getDefault())
        val yearFormatter = SimpleDateFormat("yyyy", Locale.getDefault())

        val filtered = transactions.filter {
            try {
                val date = sdf.parse(it.date)
                date != null && monthFormatter.format(date) == selectedMonth && yearFormatter.format(date) == selectedYear
            } catch (_: Exception) { false }
        }

        val incomeMap = filtered.filter { it.type == "Income" }.groupBy { it.category }.mapValues { it.value.sumOf { t -> kotlin.math.abs(t.amount) } }
        val expenseMap = filtered.filter { it.type == "Expense" }.groupBy { it.category }.mapValues { it.value.sumOf { t -> kotlin.math.abs(t.amount) } }

        val allCategories = (incomeMap.keys + expenseMap.keys).distinct()
        if (allCategories.isEmpty()) {
            binding.barChart.clear()
            binding.barChart.setNoDataText("No data for $selectedMonth $selectedYear.")
            binding.barChart.invalidate()
            return
        }

        val labels = allCategories.toList()
        val incomeEntries = labels.mapIndexed { i, cat -> BarEntry(i.toFloat(), incomeMap[cat]?.toFloat() ?: 0f) }
        val expenseEntries = labels.mapIndexed { i, cat -> BarEntry(i.toFloat(), expenseMap[cat]?.toFloat() ?: 0f) }

        val incomeSet = BarDataSet(incomeEntries, "Income").apply {
            color = Color.parseColor("#66BB6A")
            valueTextColor = Color.BLACK
        }
        val expenseSet = BarDataSet(expenseEntries, "Expense").apply {
            color = Color.parseColor("#EF5350")
            valueTextColor = Color.BLACK
        }

        val barData = BarData(incomeSet, expenseSet).apply { barWidth = 0.4f }
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
                axisMinimum = -0.5f
                axisMaximum = labels.size.toFloat()
            }

            axisLeft.axisMinimum = 0f
            barData.groupBars(0f, groupSpace, barSpace)
            invalidate()
        }
    }

    private fun updateLineChart(transactions: List<Transaction>) {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val income = FloatArray(12)
        val expense = FloatArray(12)

        transactions.forEach {
            try {
                val date = sdf.parse(it.date)
                val cal = Calendar.getInstance().apply { time = date!! }
                if (cal.get(Calendar.YEAR).toString() == selectedYear) {
                    val m = cal.get(Calendar.MONTH)
                    if (it.type == "Income") income[m] += kotlin.math.abs(it.amount).toFloat()
                    else expense[m] += kotlin.math.abs(it.amount).toFloat()
                }
            } catch (_: Exception) {}
        }

        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

        val incomeEntries = income.mapIndexed { i, v -> Entry(i.toFloat(), v) }
        val expenseEntries = expense.mapIndexed { i, v -> Entry(i.toFloat(), v) }

        val incomeSet = LineDataSet(incomeEntries, "Income").apply {
            color = Color.parseColor("#66BB6A")
            setDrawValues(false)
            circleRadius = 4f
        }

        val expenseSet = LineDataSet(expenseEntries, "Expense").apply {
            color = Color.parseColor("#EF5350")
            setDrawValues(false)
            circleRadius = 4f
        }

        binding.monthlyChart.apply {
            data = LineData(incomeSet, expenseSet)
            description.isEnabled = false
            animateX(1000)
            xAxis.valueFormatter = IndexAxisValueFormatter(months)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            axisRight.isEnabled = false
            invalidate()
        }
    }

}
