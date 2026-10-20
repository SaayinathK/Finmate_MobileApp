package com.example.financetracker

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.Loan
import com.example.financetracker.models.Transaction
import com.example.financetracker.repository.LoanLedger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class LinkedDeletionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun fixture(db: AppDatabase): Pair<Loan, Loan> {
        LoanLedger(db).create("Borrower", 500.0, false, "Lent", LocalDate.now())
        val first = db.loanDao().getAllLoans().first().single()
        LoanLedger(db).settle(first.id, 100.0)
        LoanLedger(db).settle(first.id, 400.0)
        LoanLedger(db).create("Other", 80.0, true, "Borrowed", LocalDate.now())
        db.transactionDao().insertTransaction(Transaction(title = "Ordinary", amount = -12.0, category = "Other", date = "20 Oct 2026", type = "Expense"))
        return first to db.loanDao().getAllLoans().first().first { it.id != first.id }
    }
    private suspend fun assertOtherSurvives(db: AppDatabase, deleted: Loan, other: Loan) {
        assertNull(db.loanDao().getById(deleted.id))
        assertNotNull(db.loanDao().getById(other.id))
        val entries = db.transactionDao().getAllTransactionsRaw()
        assertEquals(2, entries.size)
        assertTrue(entries.none { it.loanId == deleted.id })
        assertTrue(entries.any { it.loanId == other.id })
        assertTrue(entries.any { it.title == "Ordinary" })
    }
    @Test fun deletingSettledLoanRemovesOpeningAndAllPaymentsOnly() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val (first, other) = fixture(db)
            db.loanDao().deleteLoanById(first.id)
            db.loanDao().deleteLoanById(first.id) // Retry is harmless.
            assertOtherSurvives(db, first, other)
        } finally { db.close() }
    }
    @Test fun deletingEitherOpeningOrRepaymentRemovesTheWholeLinkedLoan() = runBlocking {
        for (event in listOf("OPEN", "SETTLE")) {
            val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            try {
                val (first, other) = fixture(db)
                val entry = db.transactionDao().getAllTransactionsRaw().first { it.loanId == first.id && it.loanEvent == event }
                // A stale caller-supplied link must not select a different loan for deletion.
                db.transactionDao().deleteTransaction(entry.copy(loanId = other.id, splitGroup = "bill:forged"))
                db.transactionDao().deleteTransaction(entry)
                assertOtherSurvives(db, first, other)
            } finally { db.close() }
        }
    }
    @Test fun deletingOrdinaryTransactionLeavesBothLoans() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            fixture(db)
            db.transactionDao().deleteTransaction(db.transactionDao().getAllTransactionsRaw().first { it.title == "Ordinary" })
            assertEquals(2, db.loanDao().getAllLoans().first().size)
            assertEquals(4, db.transactionDao().getAllTransactionsRaw().size)
        } finally { db.close() }
    }
    @Test fun sharedBillCanBeDeletedFromExpenseLoanOrPayment() = runBlocking {
        for (side in listOf("expense", "loan", "payment")) {
            val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            try {
                val group = "bill:test"
                val a = db.loanDao().insertLoan(Loan(personName = "A", amount = 50.0, type = "GIVVEN", sharedBillGroup = group)).toInt()
                val b = db.loanDao().insertLoan(Loan(personName = "B", amount = 50.0, type = "GIVVEN", sharedBillGroup = group)).toInt()
                db.transactionDao().insertTransaction(Transaction(title = "Bill", amount = -150.0, category = "Food", date = "20 Oct 2026", type = "Expense", splitGroup = group))
                LoanLedger(db).settle(a, 10.0); LoanLedger(db).settle(b, 20.0)
                LoanLedger(db).create("Other", 90.0, true, "Unrelated", LocalDate.now())
                val txs = db.transactionDao().getAllTransactionsRaw()
                when (side) {
                    "loan" -> db.loanDao().deleteLoanById(a)
                    "expense" -> db.transactionDao().deleteTransaction(txs.first { it.splitGroup == group })
                    else -> db.transactionDao().deleteTransaction(txs.first { it.loanId == b })
                }
                assertEquals("Other", db.loanDao().getAllLoans().first().single().personName)
                assertEquals("Unrelated", db.transactionDao().getAllTransactionsRaw().single().title)
            } finally { db.close() }
        }
    }
    @Test fun failureDuringLoanDeletionRollsBackHistoryDeletion() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val (first, _) = fixture(db)
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_delete BEFORE DELETE ON loans BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            var failed = false
            try { db.loanDao().deleteLoanById(first.id) } catch (_: Exception) { failed = true }
            assertTrue(failed)
            assertNotNull(db.loanDao().getById(first.id))
            assertEquals(3, db.transactionDao().getAllTransactionsRaw().count { it.loanId == first.id })
        } finally { db.close() }
    }
    @Test fun clearingHistoryAlsoRemovesUnpaidSharedBillLoans() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val group = "bill:unpaid"
            db.loanDao().insertLoan(Loan(personName = "Bill member", amount = 25.0, type = "GIVVEN", sharedBillGroup = group))
            db.loanDao().insertLoan(Loan(personName = "Legacy", amount = 80.0, type = "RECEIVED"))
            db.transactionDao().insertTransaction(Transaction(title = "Bill", amount = -50.0, category = "Food", date = "20 Oct 2026", type = "Expense", splitGroup = group))
            db.transactionDao().deleteAll()
            assertTrue(db.transactionDao().getAllTransactionsRaw().isEmpty())
            assertEquals("Legacy", db.loanDao().getAllLoans().first().single().personName)
        } finally { db.close() }
    }
}
