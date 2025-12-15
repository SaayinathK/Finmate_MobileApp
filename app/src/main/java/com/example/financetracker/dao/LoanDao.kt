package com.example.financetracker.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Query
import com.example.financetracker.models.Loan
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {
    @Insert
    suspend fun insertLoan(loan: Loan)

    @Query("SELECT * FROM loans")
    fun getAllLoans(): Flow<List<Loan>>

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteLoanById(id: Int)

    @Update
    suspend fun updateLoan(loan: Loan)
}
