package com.example.financetracker.repository

import androidx.room.withTransaction
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.Loan
import com.example.financetracker.utils.LoanEditRules
import com.example.financetracker.utils.PlannerRules
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** Edit both sides of a stored financial relationship in the same database transaction. */
class FinancialEditor(private val db: AppDatabase) {
    suspend fun editLoan(id: Int, person: String, originalCents: Long, borrowed: Boolean, note: String, date: LocalDate) = db.withTransaction {
        val loan = requireNotNull(db.loanDao().getById(id)) { "Loan no longer exists" }
        require(person.trim().isNotEmpty()) { "Enter the person's name" }
        val remaining = LoanEditRules.remainingAfterPrincipalEdit(LoanEditRules.cents(loan.originalAmount), LoanEditRules.cents(loan.amount), originalCents)
        val entries = db.transactionDao().getForLoan(id)
        require(entries.all { it.loanEvent in listOf("OPEN", "SETTLE") }) { "This loan has an unsupported history entry" }
        require(entries.count { it.loanEvent == "OPEN" } <= 1) { "This loan has multiple opening entries" }
        require(entries.filter { it.loanEvent == "SETTLE" }.all { PlannerRules.parseDate(it.date)?.let { d -> date <= d } == true }) {
            "Loan date cannot be after an existing payment"
        }
        loan.sharedBillGroup?.let { group ->
            require(!borrowed) { "Shared bill loans must remain amounts to collect" }
            val bill = db.transactionDao().getForSplitGroup(group).singleOrNull()
            requireNotNull(bill) { "Shared bill no longer exists" }
            require(PlannerRules.parseDate(bill.date) == date) { "Edit the shared bill transaction to change its date" }
            val otherShares = db.loanDao().getForBill(group).filter { it.id != id }
                .fold(0L) { total, item -> Math.addExact(total, LoanEditRules.cents(item.originalAmount)) }
            require(otherShares <= LoanEditRules.cents(abs(bill.amount)) && originalCents <= LoanEditRules.cents(abs(bill.amount)) - otherShares) {
                "People's shares cannot exceed the shared bill total"
            }
        }
        val updated = loan.copy(personName = person.trim(), originalAmount = originalCents / 100.0, amount = remaining / 100.0,
            type = if (borrowed) "RECEIVED" else "GIVVEN", note = note.trim(), date = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        db.loanDao().updateLoan(updated)
        entries.forEach { tx ->
            if (tx.loanEvent == "OPEN") db.transactionDao().updateTransaction(tx.copy(
                title = note.trim().ifBlank { if (borrowed) "Borrowed from ${updated.personName}" else "Lent to ${updated.personName}" },
                amount = updated.originalAmount * if (borrowed) 1 else -1, type = if (borrowed) "Income" else "Expense", date = PlannerRules.displayDate(date)))
            else {
                val automaticTitle = tx.title == "Repaid ${loan.personName}" || tx.title == "Collected from ${loan.personName}"
                db.transactionDao().updateTransaction(tx.copy(
                    title = if (automaticTitle) (if (borrowed) "Repaid ${updated.personName}" else "Collected from ${updated.personName}") else tx.title,
                    amount = abs(tx.amount) * if (borrowed) -1 else 1, type = if (borrowed) "Expense" else "Income"))
            }
        }
    }

    suspend fun editTransaction(id: Int, title: String, amountCents: Long, category: String, date: LocalDate, income: Boolean) = db.withTransaction {
        val tx = requireNotNull(db.transactionDao().getTransactionById(id)) { "Transaction no longer exists" }
        require(title.trim().isNotEmpty() && category.trim().isNotEmpty()) { "Enter a description and category" }
        require(amountCents > 0) { "Enter a positive amount" }
        val loan = tx.loanId?.let { requireNotNull(db.loanDao().getById(it)) { "Linked loan no longer exists" } }
        if (loan != null) {
            when (tx.loanEvent) {
                "OPEN" -> {
                    require(income == (loan.type == "RECEIVED")) { "Change the loan direction in Edit loan" }
                    editLoan(loan.id, loan.personName, amountCents, loan.type == "RECEIVED", title, date)
                }
                "SETTLE" -> {
                    require(income == (loan.type != "RECEIVED")) { "Payment direction follows the loan" }
                    val start = loan.sharedBillGroup?.let { group -> db.transactionDao().getForSplitGroup(group).singleOrNull()?.date?.let(PlannerRules::parseDate) }
                        ?: java.time.Instant.ofEpochMilli(loan.date).atZone(ZoneId.systemDefault()).toLocalDate()
                    require(date >= start) { "Payment date cannot be before the loan date" }
                    val remaining = LoanEditRules.remainingAfterPaymentEdit(LoanEditRules.cents(loan.originalAmount), LoanEditRules.cents(loan.amount), LoanEditRules.cents(abs(tx.amount)), amountCents)
                    db.loanDao().updateLoan(loan.copy(amount = remaining / 100.0))
                }
                else -> error("This linked transaction cannot be edited safely")
            }
        } else if (tx.splitGroup?.startsWith("bill:") == true) {
            require(!income) { "Shared bills must remain expenses" }
            val loans = db.loanDao().getForBill(tx.splitGroup)
            val shares = loans.fold(0L) { total, item -> Math.addExact(total, LoanEditRules.cents(item.originalAmount)) }
            require(amountCents >= shares) { "Bill total cannot be less than people's existing shares" }
            loans.forEach { member ->
                require(db.transactionDao().getForLoan(member.id).all { payment -> PlannerRules.parseDate(payment.date)?.let { it >= date } == true }) {
                    "Bill date cannot be after an existing collection"
                }
                val oldAutomaticNote = "Shared bill: ${tx.title} (${tx.date})"
                db.loanDao().updateLoan(member.copy(
                    date = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    note = if (member.note == oldAutomaticNote) "Shared bill: ${title.trim()} (${PlannerRules.displayDate(date)})" else member.note))
            }
        }
        // Copy stored link fields; edits never detach the entry from its loan or split group.
        db.transactionDao().updateTransaction(tx.copy(title = title.trim(), amount = amountCents / 100.0 * if (income) 1 else -1,
            category = category.trim(), date = PlannerRules.displayDate(date), type = if (income) "Income" else "Expense"))
    }
}
