package com.example.financetracker.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.financetracker.models.Transaction

@Dao
interface TransactionDao {

    // Real-time LiveData for UI observation
    @Query("SELECT * FROM transactions ORDER BY id DESC")
    fun getAllTransactions(): LiveData<List<Transaction>>

    // Raw list for background operations like export/import
    @Query("SELECT * FROM transactions ORDER BY id DESC")
    suspend fun getAllTransactionsRaw(): List<Transaction>

    // Get specific transaction by ID
    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    suspend fun getTransactionById(transactionId: Int): Transaction?

    // Insert a single transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    // Insert multiple transactions (for Excel import)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<Transaction>)

    // Update a transaction
    @Update
    suspend fun updateTransaction(transaction: Transaction)

    // Delete a transaction
    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    // Delete all transactions
    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
