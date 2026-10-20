package com.example.financetracker.activities

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.Transaction
import com.example.financetracker.R
import com.example.financetracker.ui.WalletScreen
import com.example.financetracker.utils.PlannerRules
import com.example.financetracker.utils.ReportPeriod
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

open class ReportsActivity : WalletScreen() {
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var period = ReportPeriod.MONTH
    private var anchor = LocalDate.now()
    private var type = 0
    private var query = ""
    private var exportSnapshot = ""
    private var exportLabel = ""
    private var renderJob: Job? = null
    private val history get() = this is TransactionHistoryActivity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        period = ReportPeriod.values()[savedInstanceState?.getInt("period") ?: 2]
        anchor = savedInstanceState?.getString("anchor")?.let(LocalDate::parse) ?: LocalDate.now()
        type = savedInstanceState?.getInt("type") ?: 0
        query = savedInstanceState?.getString("query") ?: ""
        exportSnapshot = savedInstanceState?.getString("exportSnapshot") ?: ""
        exportLabel = savedInstanceState?.getString("exportLabel") ?: ""
        db.transactionDao().getAllTransactions().observe(this) { render() }
    }
    override fun onResume() { super.onResume(); render() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("period", period.ordinal); outState.putString("anchor", anchor.toString()); outState.putInt("type", type); outState.putString("query", query)
        outState.putString("exportSnapshot", exportSnapshot); outState.putString("exportLabel", exportLabel); super.onSaveInstanceState(outState)
    }
    private fun render() {
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            val (start, end) = PlannerRules.bounds(anchor, period)
            val transactions = db.transactionDao().getAllTransactionsRaw().filter { PlannerRules.parseDate(it.date)?.let { d -> d in start..end } == true }
            screen(if (history) "Transactions" else "Reports", floatingAction = if (history) "Add transaction" to { open(AddTransactionActivity::class.java) } else null)
            periodPicker(period, anchor) { p,d -> period = p; anchor = d; render() }
            val income = transactions.filter { it.type == "Income" }.sumOf { abs(it.amount) }
            val expense = transactions.filter { it.type == "Expense" }.sumOf { abs(it.amount) }
            card().apply {
                addView(text("Net balance", 13, muted)); addView(text(money(income - expense), 28, blue, true))
                addView(text("Income  ${money(income)}", 15, green, true)); addView(text("Expenses  ${money(expense)}", 15, red, true))
                addView(text("${transactions.size} recorded transactions", 12, muted))
            }
            if (!history && transactions.isNotEmpty()) {
                section("Cash flow")
                val bar = BarChart(this@ReportsActivity).apply {
                    data = BarData(BarDataSet(listOf(BarEntry(0f, income.toFloat()), BarEntry(1f, expense.toFloat())), "Cash flow").apply { colors = listOf(green, red); valueTextSize = 11f })
                    description.isEnabled = false; legend.isEnabled = false; axisRight.isEnabled = false; axisLeft.axisMinimum = 0f
                    xAxis.apply { position = XAxis.XAxisPosition.BOTTOM; valueFormatter = IndexAxisValueFormatter(listOf("Income", "Expenses")); granularity = 1f; setDrawGridLines(false) }
                    setFitBars(true); setPinchZoom(false); setScaleEnabled(false)
                }
                styleChart(bar)
                card().addView(bar, LinearLayout.LayoutParams(-1, dp(210)))
                if (android.animation.ValueAnimator.areAnimatorsEnabled()) bar.animateY(600)
                val grouped = transactions.filter { it.type == "Expense" }.groupBy { it.category }.mapValues { (_, tx) -> tx.sumOf { abs(it.amount) } }.toList().sortedByDescending { it.second }
                if (grouped.isNotEmpty()) {
                    section("Expense categories")
                    val pie = PieChart(this@ReportsActivity).apply {
                        data = PieData(PieDataSet(grouped.map { PieEntry(it.second.toFloat(), it.first) }, "Expenses").apply { colors = listOf(blue, green, Color.rgb(255,184,66), red, Color.rgb(122,102,219), Color.rgb(0,169,182)); valueTextColor = Color.WHITE; valueTextSize = 11f; sliceSpace = 3f })
                        description.isEnabled = false; centerText = "Expenses"; setCenterTextColor(muted); setEntryLabelColor(ink); setDrawEntryLabels(false)
                        legend.isWordWrapEnabled = true; holeRadius = 66f; transparentCircleRadius = 70f
                    }
                    styleChart(pie); pie.setHoleColor(surface)
                    card().addView(pie, LinearLayout.LayoutParams(-1, dp(270)))
                    if (android.animation.ValueAnimator.areAnimatorsEnabled()) pie.animateY(700)
                    grouped.forEach { (category, amount) -> card().apply { addView(text(category, 15, ink, true)); addView(text("${money(amount)} · ${"%.1f".format(amount / expense * 100)}%", 14, muted)) } }
                }
                section("Balance trend", "Cumulative, from zero")
                val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.toList()
                var total = 0.0
                val lineEntries = dates.mapIndexed { i, d -> total += transactions.filter { PlannerRules.parseDate(it.date) == d }.sumOf { if (it.type == "Income") abs(it.amount) else -abs(it.amount) }; Entry(i.toFloat(), total.toFloat()) }
                val line = LineChart(this@ReportsActivity).apply {
                    data = LineData(LineDataSet(lineEntries, "Balance").apply { color = blue; lineWidth = 3f; setCircleColor(blue); circleRadius = 3f; setDrawValues(false); setDrawFilled(true); fillColor = blue; fillAlpha = 25 })
                    description.isEnabled = false; axisRight.isEnabled = false; legend.isEnabled = false
                    xAxis.apply { position = XAxis.XAxisPosition.BOTTOM; valueFormatter = IndexAxisValueFormatter(dates.map { it.format(DateTimeFormatter.ofPattern("d MMM")) }); granularity = 1f; setLabelCount(4, false); setDrawGridLines(false) }
                    setScaleEnabled(false)
                }
                styleChart(line)
                card().addView(line, LinearLayout.LayoutParams(-1, dp(220)))
                if (android.animation.ValueAnimator.areAnimatorsEnabled()) line.animateX(600)
            }
            if (!history) {
                val tasks = db.taskDao().allTasks()
                val done = db.taskDao().completions().filter { LocalDate.parse(it.occurrenceDate) in start..end }
                val days = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= minOf(end, LocalDate.now()) }.toList()
                val due = days.sumOf { d -> (tasks.filter { PlannerRules.occurs(it, d) }.map { it.id } + done.filter { it.occurrenceDate == d.toString() }.map { it.taskId }).distinct().size }
                card(color = Color.rgb(231,247,240)).apply {
                    section("Tasks", "${done.size} completed · $due due so far", this)
                    addView(text(if (due == 0) "No tasks due" else "${(done.size * 100 / due).coerceAtMost(100)}% completion", 22, green, true))
                    button("View tasks", this, true) { open(TasksActivity::class.java) }
                }
                if (days.isNotEmpty() && (due > 0 || done.isNotEmpty())) {
                    val chart = BarChart(this@ReportsActivity).apply {
                        data = BarData(BarDataSet(days.mapIndexed { index, d ->
                            val completed = done.count { it.occurrenceDate == d.toString() }
                            val pending = (tasks.count { PlannerRules.occurs(it, d) } - completed).coerceAtLeast(0)
                            BarEntry(index.toFloat(), floatArrayOf(completed.toFloat(), pending.toFloat()))
                        }, "Tasks").apply {
                            colors = listOf(green, Color.rgb(206, 220, 237)); stackLabels = arrayOf("Completed", "Unfinished"); setDrawValues(false)
                        })
                        description.isEnabled = false; axisRight.isEnabled = false; axisLeft.axisMinimum = 0f; axisLeft.granularity = 1f
                        xAxis.apply { position = XAxis.XAxisPosition.BOTTOM; granularity = 1f; setDrawGridLines(false); valueFormatter = IndexAxisValueFormatter(days.map { it.format(DateTimeFormatter.ofPattern("d MMM")) }) }
                        setScaleEnabled(false); setFitBars(true)
                    }
                    styleChart(chart)
                    card().addView(chart, LinearLayout.LayoutParams(-1, dp(220)))
                    if (android.animation.ValueAnimator.areAnimatorsEnabled()) chart.animateY(500)
                }
                button("Export tasks", outlined = true) {
                    fun cell(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
                    exportSnapshot = "Scheduled date,Task,Notes,Completed at\r\n" + done.joinToString("\r\n") {
                        listOf(it.occurrenceDate, it.title, it.notes, java.time.Instant.ofEpochMilli(it.completedAt).atZone(java.time.ZoneId.systemDefault()).toString()).joinToString(",") { value -> cell(if (value.firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value) }
                    }
                    exportLabel = "SWallet_tasks_${start}_${end}"
                    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/csv").putExtra(Intent.EXTRA_TITLE, "$exportLabel.csv"), 701)
                }
            }
            button("Export transactions", outlined = true) {
                exportLabel = "SWallet_${start}_${end}"
                fun cell(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
                exportSnapshot = "Date,Description,Category,Type,Amount\r\n" + transactions.joinToString("\r\n") { listOf(it.date, it.title, it.category, it.type, it.amount.toString()).joinToString(",") { value -> cell(if (value.startsWith("=") || value.startsWith("+") || value.startsWith("@")) "'$value" else value) } }
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/csv").putExtra(Intent.EXTRA_TITLE, "$exportLabel.csv"), 701)
            }
            section("Transactions")
            choices(content, listOf("All", "Income", "Expense"), type) { type = it; render() }
            val search = field(content, "Search transactions", query)
            val listing = column(); content.addView(listing)
            fun showList() {
                listing.removeAllViews()
                val filtered = transactions.filter { (type == 0 || it.type == if (type == 1) "Income" else "Expense") && (it.title.contains(query, true) || it.category.contains(query, true)) }.sortedWith(compareByDescending<Transaction> { PlannerRules.parseDate(it.date) }.thenByDescending { it.id })
                if (filtered.isEmpty()) emptyState(R.drawable.art_wallet, "No transactions", parent = listing)
                filtered.forEach { tx ->
                    entry(listing, tx.title, "${tx.date} · ${tx.category}${if (tx.splitGroup != null) " · Split" else ""}", (if (tx.type == "Income") "+ " else "− ") + money(abs(tx.amount)), tx.type == "Income") {
                        if (tx.loanId != null) open(LoansActivity::class.java) else startActivity(Intent(this@ReportsActivity, AddTransactionActivity::class.java).putExtra("editMode", true).putExtra("transactionId", tx.id))
                    }
                }
            }
            showList()
            search.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString(); showList() }
                override fun afterTextChanged(s: Editable?) { }
            })
        }
    }
    private fun styleChart(chart: com.github.mikephil.charting.charts.Chart<*>) {
        chart.legend.textColor = muted
        chart.setNoDataTextColor(muted)
        chart.data?.setValueTextColor(ink)
        if (chart is com.github.mikephil.charting.charts.BarLineChartBase<*>) {
            chart.xAxis.textColor = muted; chart.axisLeft.textColor = muted; chart.axisRight.textColor = muted
            chart.xAxis.axisLineColor = border; chart.axisLeft.axisLineColor = border
            chart.axisLeft.gridColor = border; chart.axisRight.gridColor = border
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 701 && resultCode == Activity.RESULT_OK && data?.data != null) {
            lifecycleScope.launch {
                try { withContext(Dispatchers.IO) { requireNotNull(contentResolver.openOutputStream(data.data!!)).use { it.write(exportSnapshot.toByteArray(Charsets.UTF_8)) } }; toast("Report exported") }
                catch (_: Exception) { toast("Could not export report. Please try another location.") }
            }
        }
    }
}
