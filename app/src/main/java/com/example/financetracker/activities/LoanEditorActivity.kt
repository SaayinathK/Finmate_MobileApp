package com.example.financetracker.activities

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.repository.FinancialEditor
import com.example.financetracker.ui.WalletScreen
import com.example.financetracker.utils.LoanEditRules
import com.example.financetracker.utils.PlannerRules
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class LoanEditorActivity : WalletScreen() {
    private val db by lazy { AppDatabase.getDatabase(this) }
    private lateinit var person: TextInputEditText
    private lateinit var original: TextInputEditText
    private lateinit var note: TextInputEditText
    private var date = LocalDate.now()
    private var borrowed = false
    private var saving = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val id = intent.getIntExtra("loanId", -1)
            val loan = db.loanDao().getById(id)
            if (loan == null) { toast("Loan no longer exists"); finish(); return@launch }
            val billDate = loan.sharedBillGroup?.let { db.transactionDao().getForSplitGroup(it).singleOrNull()?.date?.let(PlannerRules::parseDate) }
            date = savedInstanceState?.getString("date")?.let(LocalDate::parse) ?: billDate
                ?: Instant.ofEpochMilli(loan.date).atZone(ZoneId.systemDefault()).toLocalDate()
            borrowed = savedInstanceState?.getBoolean("borrowed") ?: (loan.type == "RECEIVED")
            screen("Edit loan", "Changes also update linked transactions", nav = false)
            val form = card()
            person = field(form, "Person", savedInstanceState?.getString("person") ?: loan.personName)
            original = field(form, "Original loan amount", savedInstanceState?.getString("original") ?: "%.2f".format(Locale.US, loan.originalAmount), numeric = true)
            val paid = LoanEditRules.cents(loan.originalAmount) - LoanEditRules.cents(loan.amount)
            form.addView(text("Payments recorded: ${money(paid / 100.0)}", 14, muted))
            val remaining = text("Outstanding: ${money(loan.amount)}", 16, blue, true)
            form.addView(remaining)
            original.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val next = PlannerRules.cents(s.toString())
                    remaining.text = if (next != null && next >= paid) "New outstanding: ${money((next - paid) / 100.0)}" else "Original amount must cover recorded payments"
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            if (loan.sharedBillGroup == null) choices(form, listOf("I borrowed", "I lent"), if (borrowed) 0 else 1) { borrowed = it == 0 }
            else { borrowed = false; form.addView(text("Shared bill · To collect. Shares cannot exceed the bill total.", 13, muted)) }
            val dateButton = button("Date: ${PlannerRules.displayDate(date)}", form, true) {}
            dateButton.setOnClickListener { pickDate(date) { date = it; dateButton.text = "Date: ${PlannerRules.displayDate(it)}" } }
            if (loan.sharedBillGroup != null) {
                dateButton.isEnabled = false
                form.addView(text("Date follows the shared bill. Edit its transaction to change the date.", 13, muted))
            }
            note = field(form, "Note / description", savedInstanceState?.getString("note") ?: loan.note.orEmpty(), multiline = true)
            form.addView(text("Recorded payments stay in the history. Saving recalculates the outstanding balance.", 13, muted))
            val save = button("Save changes") {}
            save.setOnClickListener {
                if (saving) return@setOnClickListener
                val cents = PlannerRules.cents(original.text.toString())
                if (person.text.toString().isBlank()) { person.error = "Enter a name"; return@setOnClickListener }
                if (cents == null || cents < paid) { original.error = "Enter a positive amount covering recorded payments"; return@setOnClickListener }
                saving = true; save.isEnabled = false
                lifecycleScope.launch {
                    try {
                        FinancialEditor(db).editLoan(id, person.text.toString(), cents, borrowed, note.text.toString(), date)
                        toast("Loan updated"); finish()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        toast(if (error is IllegalArgumentException) error.message ?: "Check the loan details" else "Could not update loan")
                        saving = false; save.isEnabled = true
                    }
                }
            }
            button("Cancel", outlined = true) { finish() }
        }
    }
    override fun onSaveInstanceState(state: Bundle) {
        if (::person.isInitialized) {
            state.putString("person", person.text.toString()); state.putString("original", original.text.toString())
            state.putString("note", note.text.toString()); state.putString("date", date.toString()); state.putBoolean("borrowed", borrowed)
        }
        super.onSaveInstanceState(state)
    }
}
