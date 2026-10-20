package com.example.financetracker.repository

import androidx.room.withTransaction
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.Loan
import com.example.financetracker.models.Transaction
import com.example.financetracker.utils.PlannerRules
import java.time.LocalDate
import java.time.ZoneId

class LoanLedger(private val db: AppDatabase) {
    suspend fun create(person: String, amount: Double, borrowed: Boolean, note: String, date: LocalDate) = db.withTransaction {
        val loan = Loan(personName = person.trim(), amount = amount, type = if (borrowed) "RECEIVED" else "GIVVEN", note = note,
            date = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        val id = db.loanDao().insertLoan(loan).toInt()
        db.transactionDao().insertTransaction(Transaction(title = note.ifBlank { if (borrowed) "Borrowed from $person" else "Lent to $person" },
            amount = if (borrowed) amount else -amount, category = "Loans", date = PlannerRules.displayDate(date),
            type = if (borrowed) "Income" else "Expense", loanId = id, loanEvent = "OPEN"))
    }
    suspend fun settle(id: Int, amount: Double, date: LocalDate = LocalDate.now()) = db.withTransaction {
        val loan = requireNotNull(db.loanDao().getById(id)) { "Loan no longer exists" }
        val remaining = PlannerRules.remaining(loan.amount, amount)
        val paying = loan.type == "RECEIVED"
        db.transactionDao().insertTransaction(Transaction(title = if (paying) "Repaid ${loan.personName}" else "Collected from ${loan.personName}",
            amount = if (paying) -amount else amount, category = "Loans", date = PlannerRules.displayDate(date),
            type = if (paying) "Expense" else "Income", loanId = id, loanEvent = "SETTLE"))
        db.loanDao().updateLoan(loan.copy(amount = remaining))
    }
}
