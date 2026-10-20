package com.example.financetracker.activities

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.R
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.ui.WalletScreen
import com.example.financetracker.ui.WalletMotion
import com.example.financetracker.ui.WalletGuideView
import com.example.financetracker.utils.PlannerRules
import com.example.financetracker.utils.ReportPeriod
import com.example.financetracker.receiver.NotificationScheduler
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class HomeActivity : WalletScreen() {
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var period = ReportPeriod.MONTH
    private var anchor = LocalDate.now()
    private var renderJob: Job? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        period = ReportPeriod.values()[savedInstanceState?.getInt("period") ?: 2]
        anchor = savedInstanceState?.getString("anchor")?.let(LocalDate::parse) ?: LocalDate.now()
        db.transactionDao().getAllTransactions().observe(this) { render() }
        lifecycleScope.launch { combine(db.taskDao().observeTasks(), db.taskDao().observeCompletions()) { a, b -> a to b }.collect { render() } }
        NotificationScheduler.scheduleDailyReminder(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !getPreferences(MODE_PRIVATE).getBoolean("asked_notifications", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("asked_notifications", true).apply()
            getSharedPreferences("finmate_settings", MODE_PRIVATE).edit().putBoolean("notification_permission_requested", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 31)
        }
    }
    override fun onResume() { super.onResume(); render() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("period", period.ordinal); outState.putString("anchor", anchor.toString()); super.onSaveInstanceState(outState) }
    private fun render() {
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            val transactions = db.transactionDao().getAllTransactionsRaw()
            val tasks = db.taskDao().allTasks()
            val completions = db.taskDao().completions()
            val prefs = getSharedPreferences("finmate_settings", MODE_PRIVATE)
            if (!prefs.contains("currency")) prefs.edit().putString("currency", db.budgetDao().getAllBudgets().first().firstOrNull()?.currency ?: "LKR").apply()
            val (start, end) = PlannerRules.bounds(anchor, period)
            val filtered = transactions.filter { PlannerRules.parseDate(it.date)?.let { d -> d in start..end } == true }
            val income = filtered.filter { it.type == "Income" }.sumOf { kotlin.math.abs(it.amount) }
            val expense = filtered.filter { it.type == "Expense" }.sumOf { kotlin.math.abs(it.amount) }
            screen("SWallet", LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), floatingAction = "Add transaction" to { open(AddTransactionActivity::class.java) })
            periodPicker(period, anchor) { p, d -> period = p; anchor = d; render() }
            val balance = card(color = blue).apply {
                setBackgroundResource(R.drawable.wallet_hero_bg); clipToOutline = true
            }
            val hero = row()
            hero.addView(column().apply {
                addView(text("Net balance", 12, Color.rgb(213,237,255)))
                addView(text(money(income - expense), 26, Color.WHITE, true))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            hero.addView(guide(WalletGuideView.Gesture.WELCOME, 108))
            balance.addView(hero)
            val stats = row()
            listOf(Triple("Income", income, green), Triple("Expenses", expense, red)).forEach { (label, amount, color) ->
                val c = card(stats, if (color == green) Color.rgb(230,247,240) else Color.rgb(255,237,240))
                c.layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), dp(8)) }
                val heading = row()
                heading.addView(badge(if (color == green) R.drawable.ic_arrow_in else R.drawable.ic_arrow_out, color, Color.TRANSPARENT), LinearLayout.LayoutParams(dp(24), dp(24)))
                heading.addView(text(label, 12, color, true))
                c.addView(heading); c.addView(text(money(amount), 17, color, true))
            }
            content.addView(stats)
            val today = LocalDate.now()
            val due = tasks.filter { it.important && PlannerRules.occurs(it, today, completions) }
            val done = due.count { PlannerRules.isCompleted(it, today, completions) }
            val checklist = card(color = Color.rgb(231, 240, 255))
            val taskHeader = row()
            taskHeader.addView(column().apply {
                section("Important today", "$done / ${due.size} done", this)
                addView(ProgressBar(this@HomeActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = due.size.coerceAtLeast(1)
                    progressTintList = android.content.res.ColorStateList.valueOf(blue)
                    setProgress(done, WalletMotion.enabled())
                }, LinearLayout.LayoutParams(-1, dp(6)))

            }, LinearLayout.LayoutParams(0, -2, 1f))
            taskHeader.addView(illustration(R.drawable.art_checklist, 96))
            checklist.addView(taskHeader)
            if (due.isEmpty()) checklist.addView(text("No important tasks today. Mark tasks as important in their details.", 13, muted))
            due.forEach { task ->
                val completed = PlannerRules.isCompleted(task, today, completions)
                val item = card(checklist).apply { tag = "todayTask:${task.id}" }
                item.addView(text((if (completed) "\u2713  " else "\u25cb  ") + task.title, 14, if (completed) green else ink, true))
                item.addView(text(PlannerRules.taskReminder(task), 12, muted))
                item.minimumHeight = dp(56); item.isFocusable = true
                item.contentDescription = "Edit ${task.title}"
                item.setOnClickListener { startActivity(android.content.Intent(this@HomeActivity, TaskEditorActivity::class.java).putExtra("taskId", task.id)) }
                WalletMotion.press(item)
            }

            button("View tasks", checklist, true) { open(TasksActivity::class.java) }
            val budgetKey = "${anchor.year}_${anchor.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH))}"
            val budget = db.budgetDao().getBudgetByKey(budgetKey)
            val budgetCard = card()
            val monthExpense = transactions.filter { it.type == "Expense" && PlannerRules.parseDate(it.date)?.let { d -> d.year == anchor.year && d.month == anchor.month } == true }.sumOf { kotlin.math.abs(it.amount) }
            section("Budget", if (budget != null && budget.amount > 0) "${money(monthExpense)} / ${money(budget.amount)}" else "No monthly limit", budgetCard)
            if (budget != null && budget.amount > 0) {
                budgetCard.addView(ProgressBar(this@HomeActivity, null, android.R.attr.progressBarStyleHorizontal).apply { progress = (monthExpense / budget.amount * 100).toInt().coerceIn(0,100) }, LinearLayout.LayoutParams(-1, dp(8)))
                budgetCard.addView(text(if (monthExpense > budget.amount) "Over budget by ${money(monthExpense - budget.amount)}" else "${money(budget.amount - monthExpense)} left to spend", 13, if (monthExpense > budget.amount) red else green))
            }
            button("Edit budget", budgetCard, true) { open(BudgetSetupActivity::class.java) }
            gap()
            section("Recent activity", "${filtered.size} transactions")
            if (filtered.isEmpty()) emptyState(R.drawable.art_wallet, "No transactions", "Add income or an expense")
            filtered.sortedByDescending { PlannerRules.parseDate(it.date) }.take(4).forEach { tx ->
                val editEntry = { startActivity(android.content.Intent(this@HomeActivity, TransactionEditorActivity::class.java).putExtra("transactionId", tx.id)) }
                entry(title = tx.title, detail = "${tx.category} · ${tx.date}", amount = (if (tx.type == "Income") "+ " else "− ") + money(kotlin.math.abs(tx.amount)), incoming = tx.type == "Income", editAction = editEntry) { editEntry() }
            }
        }
    }
}
