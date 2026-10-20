package com.example.financetracker.activities

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.repository.FinancialEditor
import com.example.financetracker.ui.WalletScreen
import com.example.financetracker.utils.CategoryStore
import com.example.financetracker.utils.PlannerRules
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

class TransactionEditorActivity : WalletScreen() {
    private val db by lazy { AppDatabase.getDatabase(this) }
    private lateinit var titleInput: TextInputEditText
    private lateinit var amount: TextInputEditText
    private lateinit var category: TextInputEditText
    private var date = LocalDate.now()
    private var income = false
    private var saving = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val id = intent.getIntExtra("transactionId", -1)
            val tx = db.transactionDao().getTransactionById(id)
            if (tx == null) { toast("Transaction no longer exists"); finish(); return@launch }
            income = savedInstanceState?.getBoolean("income") ?: (tx.type == "Income")
            date = savedInstanceState?.getString("date")?.let(LocalDate::parse) ?: PlannerRules.parseDate(tx.date) ?: LocalDate.now()
            screen("Edit transaction", if (tx.loanEvent == "SETTLE") "Payment and loan balance update together" else "Review and save your changes", nav = false)
            val form = card()
            if (tx.loanId == null && tx.splitGroup?.startsWith("bill:") != true) choices(form, listOf("Expense", "Income"), if (income) 1 else 0) { income = it == 1 }
            else {
                income = tx.type == "Income"
                form.addView(text("${tx.type} · Linked ${if (tx.loanEvent == "SETTLE") "payment" else if (tx.loanId != null) "loan" else "shared bill"}", 14, blue, true))
            }
            titleInput = field(form, "Description", savedInstanceState?.getString("title") ?: tx.title)
            amount = field(form, "Amount", savedInstanceState?.getString("amount") ?: "%.2f".format(Locale.US, abs(tx.amount)), numeric = true)
            category = field(form, "Category", savedInstanceState?.getString("category") ?: tx.category)
            val categories = CategoryStore.all(this@TransactionEditorActivity, db.transactionDao().getAllTransactionsRaw().map { it.category })
            choices(form, categories.take(5), -1) { category.setText(categories[it]) }
            val dateButton = button("Date: ${PlannerRules.displayDate(date)}", form, true) {}
            dateButton.setOnClickListener { pickDate(date) { date = it; dateButton.text = "Date: ${PlannerRules.displayDate(it)}" } }
            if (tx.loanId != null) {
                form.addView(text(if (tx.loanEvent == "SETTLE") "The outstanding loan balance adjusts to this payment. Other payments stay unchanged." else "The original loan amount adjusts with this entry. Existing payments stay recorded.", 13, muted))
                button("Edit linked loan", form, true) {
                    startActivity(Intent(this@TransactionEditorActivity, LoanEditorActivity::class.java).putExtra("loanId", tx.loanId)); finish()
                }
            } else if (tx.splitGroup?.startsWith("bill:") == true) form.addView(text("Other people's shares stay unchanged. The difference updates your share of the bill.", 13, muted))
            val save = button("Save changes") {}
            save.setOnClickListener {
                if (saving) return@setOnClickListener
                val cents = PlannerRules.cents(amount.text.toString())
                if (titleInput.text.toString().isBlank()) { titleInput.error = "Enter a description"; return@setOnClickListener }
                if (cents == null) { amount.error = "Enter a positive amount with up to two decimal places"; return@setOnClickListener }
                if (category.text.toString().isBlank()) { category.error = "Enter a category"; return@setOnClickListener }
                saving = true; save.isEnabled = false
                lifecycleScope.launch {
                    try {
                        FinancialEditor(db).editTransaction(id, titleInput.text.toString(), cents, category.text.toString(), date, income)
                        CategoryStore.remember(this@TransactionEditorActivity, listOf(category.text.toString()))
                        toast("Transaction updated"); finish()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        toast(if (error is IllegalArgumentException) error.message ?: "Check the transaction details" else "Could not update transaction")
                        saving = false; save.isEnabled = true
                    }
                }
            }
            button("Delete transaction", outlined = true) {
                val linked = tx.loanId != null || tx.splitGroup?.startsWith("bill:") == true
                confirmDeletion("Delete transaction?", if (linked) "This also deletes the linked loan and all its payment history. For a shared bill, the entire bill and all related loans and payments are removed." else "This permanently removes this transaction from your accounts.") {
                    lifecycleScope.launch {
                        try { db.transactionDao().deleteTransaction(tx); toast("Transaction deleted"); finish() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { toast("Could not delete transaction") }
                    }
                }
            }.setTextColor(red)
            button("Cancel", outlined = true) { finish() }
        }
    }
    override fun onSaveInstanceState(state: Bundle) {
        if (::titleInput.isInitialized) {
            state.putString("title", titleInput.text.toString()); state.putString("amount", amount.text.toString())
            state.putString("category", category.text.toString()); state.putString("date", date.toString()); state.putBoolean("income", income)
        }
        super.onSaveInstanceState(state)
    }
}
