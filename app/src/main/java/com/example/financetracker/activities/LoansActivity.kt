package com.example.financetracker.activities

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.Loan
import com.example.financetracker.R
import com.example.financetracker.repository.LoanLedger
import com.example.financetracker.ui.WalletScreen
import com.example.financetracker.utils.PlannerRules
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class LoansActivity : WalletScreen() {
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var mode = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = savedInstanceState?.getInt("mode") ?: 0
        lifecycleScope.launch { db.loanDao().getAllLoans().collect { render(it) } }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("mode", mode); super.onSaveInstanceState(outState) }
    private fun render(loans: List<Loan>) {
        screen("Loans", floatingAction = "Add loan" to {
            startActivity(android.content.Intent(this, AddTransactionActivity::class.java).putExtra("createLoan", true))
        })
        val owed = loans.filter { it.type != "RECEIVED" }.sumOf { it.amount }
        val owe = loans.filter { it.type == "RECEIVED" }.sumOf { it.amount }
        card().apply {
            val totals = row()
            totals.addView(column().apply { addView(text("To collect", 12, muted)); addView(text(money(owed), 20, green, true)) }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            totals.addView(column().apply { addView(text("To repay", 12, muted)); addView(text(money(owe), 20, red, true)) }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            addView(totals)
        }
        choices(content, listOf("All", "To collect", "To repay", "Settled"), mode) { mode = it; render(loans) }
        val filtered = loans.filter { when(mode) { 1 -> it.type != "RECEIVED" && it.amount > 0; 2 -> it.type == "RECEIVED" && it.amount > 0; 3 -> it.amount <= 0; else -> it.amount > 0 } }
        if (filtered.isEmpty()) emptyState(R.drawable.art_wallet, "No loans", "Track borrowing and lending")
        filtered.groupBy { it.personName.trim().lowercase() }.forEach { (_, entries) ->
            val person = entries.first().personName
            section(person)
            entries.sortedByDescending { it.date }.forEach { loan ->
                val paying = loan.type == "RECEIVED"
                card().apply {
                    addView(text(if (loan.amount <= 0) "✓ Settled" else if (paying) "To repay" else "To collect", 13, if (paying) red else green, true))
                    addView(text(money(loan.amount), 24, ink, true))
                    addView(text("Original: ${money(loan.originalAmount)} · ${PlannerRules.displayDate(Instant.ofEpochMilli(loan.date).atZone(ZoneId.systemDefault()).toLocalDate())}", 12, muted))
                    loan.note?.takeIf { it.isNotBlank() }?.let { addView(text(it, 14, muted)) }
                    if (loan.amount > 0) actions(this, (if (paying) "Repay" else "Collect") to { settle(loan) }, "History" to { showHistory(loan) })
                    else button("History", this, true) { showHistory(loan) }
                    button("Edit loan", this, true) {
                        startActivity(android.content.Intent(this@LoansActivity, LoanEditorActivity::class.java).putExtra("loanId", loan.id))
                    }
                    button("Delete loan", this, true) {
                        confirmDeletion("Delete loan?", if (loan.sharedBillGroup != null) "This deletes the entire shared bill, all its related loans and payments. Account totals will change." else "This deletes this loan and every linked opening transaction, repayment and collection. Account totals will change.") {
                            lifecycleScope.launch {
                                try { db.loanDao().deleteLoanById(loan.id); toast("Loan and linked history deleted") }
                                catch (_: Exception) { toast("Could not delete loan") }
                            }
                        }
                    }.setTextColor(red)
                }
            }
        }
    }
    private fun settle(loan: Loan) {
        val form = column().apply { setPadding(dp(24), dp(8), dp(24), dp(8)) }
        form.addView(text("Outstanding: ${money(loan.amount)}", 17, ink, true))
        val amount = field(form, "Amount", "%.2f".format(java.util.Locale.US, loan.amount), true)
        val dialog = AlertDialog.Builder(this).setTitle(if (loan.type == "RECEIVED") "Return money to ${loan.personName}" else "Collect from ${loan.personName}").setView(form)
            .setPositiveButton("Record payment", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val cents = PlannerRules.cents(amount.text.toString())
                if (cents == null || cents / 100.0 > loan.amount + 0.000001) { amount.error = "Enter an amount up to ${money(loan.amount)}"; return@setOnClickListener }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                lifecycleScope.launch {
                    try { LoanLedger(db).settle(loan.id, cents / 100.0); toast("Payment recorded"); dialog.dismiss() }
                    catch (e: Exception) { amount.error = e.message ?: "Could not record payment"; dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true }
                }
            }
        }; dialog.show()
    }
    private fun showHistory(loan: Loan) {
        lifecycleScope.launch {
            val entries = db.transactionDao().getForLoan(loan.id).sortedBy { it.id }
            val history = column().apply { setPadding(dp(20), dp(8), dp(20), dp(8)) }
            history.addView(text("Original: ${money(loan.originalAmount)}\nOutstanding: ${money(loan.amount)}", 16, ink, true))
            if (entries.isEmpty()) history.addView(text("No linked payments", 14, muted))
            else history.addView(text("Tap an entry to edit its details.", 13, muted))
            val dialog = AlertDialog.Builder(this@LoansActivity).setTitle(loan.personName)
                .setView(android.widget.ScrollView(this@LoansActivity).apply { addView(history) }).setPositiveButton("Done", null).create()
            entries.forEach { entry ->
                button("${entry.date} · ${entry.title}\n${entry.type}: ${money(kotlin.math.abs(entry.amount))}", history, true) {
                    dialog.dismiss()
                    startActivity(android.content.Intent(this@LoansActivity, TransactionEditorActivity::class.java).putExtra("transactionId", entry.id))
                }
            }
            dialog.show()
        }
    }
}
