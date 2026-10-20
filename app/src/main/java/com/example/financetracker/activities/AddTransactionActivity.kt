package com.example.financetracker.activities

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.Loan
import com.example.financetracker.models.Transaction
import com.example.financetracker.repository.LoanLedger
import com.example.financetracker.ui.WalletScreen
import com.example.financetracker.utils.PlannerRules
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

open class AddTransactionActivity : WalletScreen() {
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var current: Transaction? = null
    private var date = LocalDate.now()
    private var income = false
    private var mode = 0
    private var loanDirection = 0
    private lateinit var titleInput: TextInputEditText
    private lateinit var amountInput: TextInputEditText
    private lateinit var categoryInput: com.google.android.material.textfield.MaterialAutoCompleteTextView
    private lateinit var personInput: TextInputEditText
    private lateinit var sharedInput: TextInputEditText
    private lateinit var isLoan: CheckBox
    private val splits = mutableListOf<Pair<TextInputEditText, TextInputEditText>>()
    private var submitting = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val edit = intent.getBooleanExtra("editMode", false)
            current = if (edit) db.transactionDao().getTransactionById(intent.getIntExtra("transactionId", -1)) else null
            if (edit && current == null) { toast("This transaction is no longer available"); finish(); return@launch }
            date = savedInstanceState?.getString("date")?.let(LocalDate::parse) ?: current?.date?.let(PlannerRules::parseDate) ?: LocalDate.now()
            income = savedInstanceState?.getBoolean("income") ?: (current?.type == "Income")
            mode = savedInstanceState?.getInt("mode") ?: 0
            loanDirection = savedInstanceState?.getInt("loanDirection") ?: 0
            screen(if (edit) "Edit transaction" else if (intent.getBooleanExtra("createLoan", false)) "Add loan" else "Add transaction", nav = false)
            if (current?.loanId != null || current?.splitGroup?.startsWith("bill:") == true) {
                card().addView(text("Manage this entry in Loans.", 16, muted))
                button("View loans") { open(LoansActivity::class.java); finish() }; return@launch
            }
            val form = card()
            choices(form, listOf("Expense", "Income"), if (income) 1 else 0) { income = it == 1 }
            titleInput = field(form, "Description", savedInstanceState?.getString("title") ?: current?.title ?: "")
            amountInput = field(form, "Amount", savedInstanceState?.getString("amount") ?: current?.amount?.let { "%.2f".format(java.util.Locale.US, kotlin.math.abs(it)) } ?: "", numeric = true)
            val categories = com.example.financetracker.utils.CategoryStore.all(this@AddTransactionActivity, db.transactionDao().getAllTransactionsRaw().map { it.category })
            val categoryBox = com.google.android.material.textfield.TextInputLayout(this@AddTransactionActivity).apply {
                hint = "Category"; boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
                boxBackgroundColor = surface; endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_DROPDOWN_MENU
                setBoxCornerRadii(dp(10).toFloat(), dp(10).toFloat(), dp(10).toFloat(), dp(10).toFloat())
            }
            categoryInput = com.google.android.material.textfield.MaterialAutoCompleteTextView(categoryBox.context).apply {
                id = com.example.financetracker.R.id.categoryInput; threshold = 1; textSize = 15f; setTextColor(ink)
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setSimpleItems(categories.toTypedArray())
                setText(savedInstanceState?.getString("category") ?: current?.category ?: if (intent.getBooleanExtra("createLoan", false)) "Loans" else "", false)
                minHeight = dp(48); setPadding(dp(12), dp(12), dp(12), dp(10))
            }
            categoryBox.addView(categoryInput); form.addView(categoryBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            choices(form, categories.take(5), -1) { categoryInput.setText(categories[it], false) }
            val dateButton = button("Date: ${PlannerRules.displayDate(date)}", form, true) { }
            dateButton.setOnClickListener { pickDate(date) { date = it; dateButton.text = "Date: ${PlannerRules.displayDate(it)}" } }
            isLoan = CheckBox(this@AddTransactionActivity).apply { text = "Borrowed or lent"; minHeight = dp(48); isChecked = savedInstanceState?.getBoolean("loan") ?: intent.getBooleanExtra("createLoan", false) }
            if (!edit) form.addView(isLoan)
            val loanFields = column()
            personInput = field(loanFields, "Person", savedInstanceState?.getString("person") ?: "")
            choices(loanFields, listOf("I borrowed", "I lent"), loanDirection) { loanDirection = it }
            loanFields.addView(text("Borrowed = income. Lent = expense.", 13, muted))
            form.addView(loanFields); loanFields.visibility = if (isLoan.isChecked) View.VISIBLE else View.GONE
            val splitCard = card()
            section("Split", parent = splitCard)
            val categorySplits = column()
            val shared = column()
            choices(splitCard, listOf("Single", "Categories", "People"), mode) { mode = it; categorySplits.visibility = if (it == 1) View.VISIBLE else View.GONE; shared.visibility = if (it == 2) View.VISIBLE else View.GONE }
            val splitRows = column()
            val restoredNames = savedInstanceState?.getStringArrayList("splitNames")
            val restoredAmounts = savedInstanceState?.getStringArrayList("splitAmounts")
            repeat(restoredNames?.size ?: 2) { addSplit(splitRows, restoredNames?.get(it) ?: "", restoredAmounts?.get(it) ?: "") }
            categorySplits.addView(splitRows)
            button("+ Category", categorySplits, true) { addSplit(splitRows) }
            button("Split equally", categorySplits, true) {
                val cents = PlannerRules.cents(amountInput.text.toString())
                if (cents == null || cents < splits.size) toast("Enter a total large enough for each share") else {
                    PlannerRules.splitEqual(cents, splits.size).forEachIndexed { i, share -> splits[i].second.setText("%.2f".format(java.util.Locale.US, share / 100.0)) }
                }
            }
            sharedInput = field(shared, "Names (comma-separated)", savedInstanceState?.getString("shared") ?: "")
            shared.addView(text("Split equally, including you. Collect others' shares in Loans; extra cents go to you first.", 14, muted))
            splitCard.addView(categorySplits); splitCard.addView(shared)
            categorySplits.visibility = if (mode == 1) View.VISIBLE else View.GONE; shared.visibility = if (mode == 2) View.VISIBLE else View.GONE
            if (edit) { splitCard.visibility = View.GONE; mode = 0 }
            isLoan.setOnCheckedChangeListener { _, checked -> loanFields.visibility = if (checked) View.VISIBLE else View.GONE; splitCard.visibility = if (checked) View.GONE else View.VISIBLE }
            if (isLoan.isChecked) splitCard.visibility = View.GONE
            button("Save") { save() }
            if (edit) button("Delete", outlined = true) {
                confirmDeletion("Delete transaction?", "This permanently removes the entry from your accounts.") {
                    lifecycleScope.launch {
                        try { db.transactionDao().deleteTransaction(current!!); toast("Transaction deleted"); finish() }
                        catch (_: Exception) { toast("Could not delete transaction") }
                    }
                }
            }
            animateEntrance(form)
        }
    }
    private fun addSplit(parent: LinearLayout, name: String = "", amount: String = "") {
        val c = column()
        val label = field(c, "Category ${splits.size + 1}", name)
        val value = field(c, "Share amount", amount, true)
        splits.add(label to value)
        button("Remove", c, true) { if (splits.size > 2) { splits.remove(label to value); parent.removeView(c) } else toast("Keep at least two shares, or choose Single") }
        parent.addView(c)
    }
    private fun save() {
        if (submitting) return
        val title = titleInput.text.toString().trim()
        val cents = PlannerRules.cents(amountInput.text.toString())
        val category = categoryInput.text.toString().trim()
        if (title.isBlank()) { titleInput.error = "Enter a description"; return }
        if (cents == null) { amountInput.error = "Enter a positive amount with up to 2 decimal places"; return }
        if (category.isBlank()) { categoryInput.error = "Choose or enter a category"; return }
        val person = personInput.text.toString().trim()
        if (isLoan.isChecked && person.isBlank()) { personInput.error = "Enter the person's name"; return }
        val shareValues = splits.map { it.first.text.toString().trim() to PlannerRules.cents(it.second.text.toString()) }
        if (!isLoan.isChecked && mode == 1 && (shareValues.any { it.first.isBlank() || it.second == null } || shareValues.sumOf { it.second ?: 0L } != cents)) {
            toast("Give each share a category and positive amount. Shares must add up exactly to the total."); return
        }
        val people = sharedInput.text.toString().split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (!isLoan.isChecked && mode == 2 && (income || people.isEmpty() || people.distinctBy { it.lowercase() }.size != people.size || people.size > 30 || cents < people.size + 1)) {
            toast("Shared bills must be expenses. Enter 1–30 different names and an amount large enough for every share."); return
        }
        submitting = true
        lifecycleScope.launch {
            try {
                db.withTransaction {
                    val amount = cents / 100.0
                    if (isLoan.isChecked) LoanLedger(db).create(person, amount, loanDirection == 0, title, date)
                    else if (mode == 1) {
                        val group = UUID.randomUUID().toString()
                        shareValues.forEach { (label, share) -> db.transactionDao().insertTransaction(Transaction(title = title, amount = (share!! / 100.0) * if (income) 1 else -1, category = label, date = PlannerRules.displayDate(date), type = if (income) "Income" else "Expense", splitGroup = group)) }
                    } else {
                        val sharedBill = mode == 2
                        val tx = Transaction(id = current?.id ?: 0, title = title, amount = if (income) amount else -amount, category = category, date = PlannerRules.displayDate(date), type = if (income) "Income" else "Expense", splitGroup = if (sharedBill) "bill:${UUID.randomUUID()}" else current?.splitGroup)
                        if (current != null) db.transactionDao().updateTransaction(tx) else db.transactionDao().insertTransaction(tx)
                        if (sharedBill) {
                            val shares = PlannerRules.splitEqual(cents, people.size + 1)
                            people.forEachIndexed { i, name -> db.loanDao().insertLoan(Loan(personName = name, amount = shares[i+1] / 100.0, type = "GIVVEN", note = "Shared bill: $title (${PlannerRules.displayDate(date)})")) }
                        }
                    }
                }
                com.example.financetracker.utils.CategoryStore.remember(this@AddTransactionActivity,
                    if (!isLoan.isChecked && mode == 1) shareValues.map { it.first } else listOf(if (isLoan.isChecked) "Loans" else category))
                toast("Transaction saved"); finish()
            } catch (_: Exception) { submitting = false; toast("Could not save. Please try again.") }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (::titleInput.isInitialized) {
            outState.putString("title", titleInput.text.toString()); outState.putString("amount", amountInput.text.toString()); outState.putString("category", categoryInput.text.toString())
            outState.putString("person", personInput.text.toString()); outState.putString("shared", sharedInput.text.toString()); outState.putBoolean("loan", isLoan.isChecked)
            outState.putBoolean("income", income); outState.putString("date", date.toString()); outState.putInt("mode", mode); outState.putInt("loanDirection", loanDirection)
            outState.putStringArrayList("splitNames", ArrayList(splits.map { it.first.text.toString() })); outState.putStringArrayList("splitAmounts", ArrayList(splits.map { it.second.text.toString() }))
        }; super.onSaveInstanceState(outState)
    }
}
