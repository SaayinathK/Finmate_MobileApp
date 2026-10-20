package com.example.financetracker.dao

import androidx.room.*
import com.example.financetracker.models.Loan
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LoanDao {
    @Insert abstract suspend fun insertLoan(loan: Loan): Long
    @Query("SELECT * FROM loans WHERE id = :id") abstract suspend fun getById(id: Int): Loan?
    @Query("SELECT * FROM loans") abstract fun getAllLoans(): Flow<List<Loan>>
    @Update abstract suspend fun updateLoan(loan: Loan)
    @Query("DELETE FROM loans WHERE id = :id") protected abstract suspend fun deleteRow(id: Int)
    @Query("DELETE FROM transactions WHERE loanId = :id") protected abstract suspend fun deleteLinkedEntries(id: Int)
    @Query("DELETE FROM transactions WHERE splitGroup = :group OR loanId IN (SELECT id FROM loans WHERE sharedBillGroup = :group)")
    protected abstract suspend fun deleteBillEntries(group: String)
    @Query("DELETE FROM loans WHERE sharedBillGroup = :group") protected abstract suspend fun deleteBillLoans(group: String)

    @Transaction
    open suspend fun deleteLoanById(id: Int) {
        val group = getById(id)?.sharedBillGroup
        if (group != null) {
            deleteBillEntries(group)
            deleteBillLoans(group)
        } else {
            deleteLinkedEntries(id)
            deleteRow(id)
        }
    }
}
