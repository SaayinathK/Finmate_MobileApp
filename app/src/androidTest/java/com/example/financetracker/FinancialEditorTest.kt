package com.example.financetracker

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.Loan
import com.example.financetracker.models.Transaction
import com.example.financetracker.repository.FinancialEditor
import com.example.financetracker.repository.LoanLedger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class FinancialEditorTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val date = LocalDate.of(2026, 10, 10)
    private suspend fun fixture(db: AppDatabase): Loan {
        LoanLedger(db).create("Person", 500.0, true, "Opening", date)
        val loan = db.loanDao().getAllLoans().first().single()
        LoanLedger(db).settle(loan.id, 100.0, date.plusDays(1))
        LoanLedger(db).settle(loan.id, 50.0, date.plusDays(2))
        return db.loanDao().getById(loan.id)!!
    }
    @Test fun loanEditUpdatesOpeningAndDirectionWithoutLosingPayments() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val loan = fixture(db)
            FinancialEditor(db).editLoan(loan.id, "New person", 60_000, false, "Corrected loan", date)
            val updated = db.loanDao().getById(loan.id)!!
            assertEquals(600.0, updated.originalAmount, 0.0); assertEquals(450.0, updated.amount, 0.0)
            val entries = db.transactionDao().getForLoan(loan.id)
            assertEquals(3, entries.size)
            assertEquals(-600.0, entries.single { it.loanEvent == "OPEN" }.amount, 0.0)
            entries.filter { it.loanEvent == "SETTLE" }.forEach {
                assertEquals("Collected from New person", it.title); assertEquals("Income", it.type); assertTrue(it.amount > 0)
            }
        } finally { db.close() }
    }
    @Test fun openingAndPaymentEditsKeepLoanInSyncAndKeepIds() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val loan = fixture(db)
            val entries = db.transactionDao().getForLoan(loan.id)
            val opening = entries.single { it.loanEvent == "OPEN" }
            val payment = entries.first { it.loanEvent == "SETTLE" && it.amount == -100.0 }
            FinancialEditor(db).editTransaction(opening.id, "Adjusted opening", 60_000, "Loans", date, true)
            assertEquals(450.0, db.loanDao().getById(loan.id)!!.amount, 0.0)
            FinancialEditor(db).editTransaction(payment.id, "Adjusted repayment", 20_000, "Loans", date.plusDays(1), false)
            assertEquals(350.0, db.loanDao().getById(loan.id)!!.amount, 0.0)
            assertEquals(entries.map { it.id }.toSet(), db.transactionDao().getForLoan(loan.id).map { it.id }.toSet())
            assertEquals(loan.id, db.transactionDao().getTransactionById(payment.id)!!.loanId)
        } finally { db.close() }
    }
    @Test fun invalidEditsLeaveLoanAndHistoryUnchanged() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val loan = fixture(db)
            val before = db.transactionDao().getForLoan(loan.id)
            var rejected = 0
            try { FinancialEditor(db).editLoan(loan.id, "Person", 14_999, true, "Invalid", date) } catch (_: IllegalArgumentException) { rejected++ }
            try { FinancialEditor(db).editLoan(loan.id, "Person", 50_000, true, "Invalid date", date.plusDays(3)) } catch (_: IllegalArgumentException) { rejected++ }
            val payment = before.first { it.loanEvent == "SETTLE" }
            try { FinancialEditor(db).editTransaction(payment.id, "Overpaid", 100_000, "Loans", date.plusDays(1), false) } catch (_: IllegalArgumentException) { rejected++ }
            assertEquals(3, rejected)
            assertEquals(loan, db.loanDao().getById(loan.id)); assertEquals(before, db.transactionDao().getForLoan(loan.id))
        } finally { db.close() }
    }
    @Test fun transactionFailureRollsBackTheLoanEdit() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val loan = fixture(db)
            val before = db.transactionDao().getForLoan(loan.id)
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_edit BEFORE UPDATE ON transactions BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            var failed = false
            try { FinancialEditor(db).editLoan(loan.id, "Other", 60_000, false, "Rollback", date) } catch (_: Exception) { failed = true }
            assertTrue(failed); assertEquals(loan, db.loanDao().getById(loan.id)); assertEquals(before, db.transactionDao().getForLoan(loan.id))
        } finally { db.close() }
    }
    @Test fun sharedBillEditsPreserveOtherSharesAndRejectImpossibleTotals() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val group = "bill:edit"
            val a = db.loanDao().insertLoan(Loan(personName = "A", amount = 50.0, type = "GIVVEN", sharedBillGroup = group)).toInt()
            val b = db.loanDao().insertLoan(Loan(personName = "B", amount = 50.0, type = "GIVVEN", sharedBillGroup = group)).toInt()
            db.transactionDao().insertTransaction(Transaction(title = "Bill", amount = -150.0, category = "Food", date = "10 Oct 2026", type = "Expense", splitGroup = group))
            val bill = db.transactionDao().getForSplitGroup(group).single()
            FinancialEditor(db).editLoan(a, "A", 7_500, false, "Adjusted share", date)
            assertEquals(50.0, db.loanDao().getById(b)!!.originalAmount, 0.0)
            FinancialEditor(db).editTransaction(bill.id, "Corrected bill", 14_000, "Food", date.plusDays(1), false)
            assertEquals(75.0, db.loanDao().getById(a)!!.originalAmount, 0.0)
            var failed = false
            try { FinancialEditor(db).editTransaction(bill.id, "Too small", 10_000, "Food", date.plusDays(1), false) } catch (_: IllegalArgumentException) { failed = true }
            assertTrue(failed); assertEquals(-140.0, db.transactionDao().getTransactionById(bill.id)!!.amount, 0.0)
        } finally { db.close() }
    }
    @Test fun ordinaryAndUnlinkedLegacyEditsDoNotInventRelationships() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val legacy = db.loanDao().insertLoan(Loan(personName = "Legacy", amount = 80.0, type = "RECEIVED")).toInt()
            FinancialEditor(db).editLoan(legacy, "Legacy corrected", 9_000, true, "", date)
            assertTrue(db.transactionDao().getAllTransactionsRaw().isEmpty())
            db.transactionDao().insertTransaction(Transaction(title = "Expense", amount = -10.0, category = "Food", date = "10 Oct 2026", type = "Expense"))
            val tx = db.transactionDao().getAllTransactionsRaw().single()
            FinancialEditor(db).editTransaction(tx.id, "Refund", 1_200, "Other", date, true)
            val result = db.transactionDao().getTransactionById(tx.id)!!
            assertEquals(12.0, result.amount, 0.0); assertNull(result.loanId); assertNull(result.splitGroup)
        } finally { db.close() }
    }
    @Test fun editorFormsSaveLoanAndPaymentChangesAfterRecreation() = runBlocking {
        org.junit.Assume.assumeFalse(com.example.financetracker.security.PinStore(context).enabled)
        val db = AppDatabase.getDatabase(context)
        val marker = "Edit fixture ${System.nanoTime()}"
        LoanLedger(db).create(marker, 500.0, true, marker, date)
        val id = db.transactionDao().getAllTransactionsRaw().single { it.title == marker }.loanId!!
        try {
            LoanLedger(db).settle(id, 100.0, date.plusDays(1))
            fun views(root: android.view.View): List<android.view.View> = listOf(root) + if (root is android.view.ViewGroup)
                (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
            fun await(check: () -> Boolean) { repeat(100) { if (check()) return; Thread.sleep(100) }; assertTrue(check()) }
            androidx.test.core.app.ActivityScenario.launch<com.example.financetracker.activities.LoanEditorActivity>(
                android.content.Intent(context, com.example.financetracker.activities.LoanEditorActivity::class.java).putExtra("loanId", id)).use { scenario ->
                fun fields(activity: android.app.Activity) = views(activity.findViewById(android.R.id.content)).filterIsInstance<com.google.android.material.textfield.TextInputEditText>()
                await { var ready = false; scenario.onActivity { ready = fields(it).size == 3 }; ready }
                scenario.onActivity { activity -> fields(activity).first { it.hint.toString() == "Original loan amount" }.setText("600.00") }
                scenario.recreate()
                await { var ready = false; scenario.onActivity { ready = fields(it).any { input -> input.hint.toString() == "Original loan amount" && input.text.toString() == "600.00" } }; ready }
                scenario.onActivity { activity -> views(activity.findViewById(android.R.id.content)).filterIsInstance<android.widget.TextView>().first { it.text.toString() == "Save changes" }.performClick() }
                await { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
            }
            assertEquals(500.0, db.loanDao().getById(id)!!.amount, 0.0)
            val payment = db.transactionDao().getForLoan(id).single { it.loanEvent == "SETTLE" }
            androidx.test.core.app.ActivityScenario.launch<com.example.financetracker.activities.TransactionEditorActivity>(
                android.content.Intent(context, com.example.financetracker.activities.TransactionEditorActivity::class.java).putExtra("transactionId", payment.id)).use { scenario ->
                await { var ready = false; scenario.onActivity { ready = views(it.findViewById(android.R.id.content)).filterIsInstance<com.google.android.material.textfield.TextInputEditText>().size == 3 }; ready }
                scenario.onActivity { activity ->
                    val all = views(activity.findViewById(android.R.id.content))
                    all.filterIsInstance<com.google.android.material.textfield.TextInputEditText>().first { it.hint.toString() == "Amount" }.setText("150.00")
                    all.filterIsInstance<android.widget.TextView>().first { it.text.toString() == "Save changes" }.performClick()
                }
                await { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
            }
            assertEquals(450.0, db.loanDao().getById(id)!!.amount, 0.0)
            assertEquals(-150.0, db.transactionDao().getTransactionById(payment.id)!!.amount, 0.0)
        } finally { db.loanDao().deleteLoanById(id) }
    }
}
