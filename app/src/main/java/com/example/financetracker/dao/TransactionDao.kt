package com.example.financetracker.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.financetracker.models.Transaction

@Dao
abstract class TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY id DESC") abstract fun getAllTransactions(): LiveData<List<Transaction>>
    @Query("SELECT * FROM transactions ORDER BY id DESC") abstract suspend fun getAllTransactionsRaw(): List<Transaction>
    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1") abstract suspend fun getTransactionById(transactionId: Int): Transaction?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertTransaction(transaction: Transaction)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertAll(transactions: List<Transaction>)
    @Update abstract suspend fun updateTransaction(transaction: Transaction)
    @Query("SELECT * FROM transactions WHERE loanId = :loanId") abstract suspend fun getForLoan(loanId: Int): List<Transaction>
    @Query("SELECT * FROM transactions WHERE splitGroup = :group") abstract suspend fun getForSplitGroup(group: String): List<Transaction>
    @Delete protected abstract suspend fun deleteRow(transaction: Transaction)
    @Query("SELECT sharedBillGroup FROM loans WHERE id = :id") protected abstract suspend fun billGroup(id: Int): String?
    @Query("DELETE FROM transactions WHERE loanId = :id") protected abstract suspend fun deleteLinkedEntries(id: Int)
    @Query("DELETE FROM loans WHERE id = :id") protected abstract suspend fun deleteLoan(id: Int)
    @Query("DELETE FROM transactions WHERE splitGroup = :group OR loanId IN (SELECT id FROM loans WHERE sharedBillGroup = :group)")
    protected abstract suspend fun deleteBillEntries(group: String)
    @Query("DELETE FROM loans WHERE sharedBillGroup = :group") protected abstract suspend fun deleteBillLoans(group: String)

    @androidx.room.Transaction
    open suspend fun deleteTransaction(transaction: Transaction) {
        // Resolve links from storage rather than a potentially stale UI copy.
        val stored = getTransactionById(transaction.id) ?: return
        val group = stored.splitGroup?.takeIf { it.startsWith("bill:") } ?: stored.loanId?.let { billGroup(it) }
        if (group != null) {
            deleteBillEntries(group)
            deleteBillLoans(group)
        } else if (stored.loanId != null) {
            deleteLinkedEntries(stored.loanId)
            deleteLoan(stored.loanId)
        } else deleteRow(stored)
    }

    @Query("DELETE FROM transactions") protected abstract suspend fun deleteAllRows()
    @Query("DELETE FROM loans WHERE id IN (SELECT loanId FROM transactions WHERE loanId IS NOT NULL) OR sharedBillGroup IN (SELECT splitGroup FROM transactions WHERE splitGroup IS NOT NULL)")
    protected abstract suspend fun deleteLoansWithHistory()
    @androidx.room.Transaction
    open suspend fun deleteAll() {
        deleteLoansWithHistory()
        deleteAllRows()
    }
}
