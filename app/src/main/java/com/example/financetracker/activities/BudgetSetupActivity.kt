package com.example.financetracker.activities

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.Budget
import com.example.financetracker.ui.WalletScreen
import com.example.financetracker.utils.PlannerRules
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class BudgetSetupActivity : WalletScreen() {
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var month = LocalDate.now().withDayOfMonth(1)
    private lateinit var amountInput: TextInputEditText
    private var submitting = false
    private var renderJob: Job? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        month = savedInstanceState?.getString("month")?.let(LocalDate::parse) ?: month
        render(savedInstanceState?.getString("amount"))
    }
    private fun render(restored: String? = null) {
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            val key = "${month.year}_${month.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH))}"
            val budget = db.budgetDao().getBudgetByKey(key)
            val expenses = db.transactionDao().getAllTransactionsRaw().filter { it.type == "Expense" && PlannerRules.parseDate(it.date)?.let { date -> date.year == month.year && date.month == month.month } == true }.sumOf { kotlin.math.abs(it.amount) }
            screen("Budget", nav = false)
            button(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), outlined = true) { pickDate(month) { month = it.withDayOfMonth(1); render() } }
            val form = card()
            section("Monthly limit", "Spent: ${money(expenses)}", form)
            amountInput = field(form, "Amount", restored ?: budget?.amount?.let { "%.2f".format(Locale.US, it) } ?: "", true)
            form.addView(text("Track usage on the dashboard.", 14, muted))
            button("Save") {
                if (submitting) return@button
                val cents = PlannerRules.cents(amountInput.text.toString())
                if (cents == null) { amountInput.error = "Enter a positive limit with up to 2 decimal places"; return@button }
                submitting = true
                lifecycleScope.launch {
                    try {
                        val currency = getSharedPreferences("finmate_settings", MODE_PRIVATE).getString("currency", "LKR") ?: "LKR"
                        db.budgetDao().insertBudget(Budget(key, cents / 100.0, currency)); toast("Budget saved"); finish()
                    } catch (_: Exception) { submitting = false; toast("Could not save budget. Please try again.") }
                }
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("month", month.toString()); if (::amountInput.isInitialized) outState.putString("amount", amountInput.text.toString()); super.onSaveInstanceState(outState)
    }
}
