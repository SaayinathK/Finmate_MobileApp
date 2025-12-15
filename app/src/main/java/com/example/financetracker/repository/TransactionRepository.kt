package com.example.financetracker.repository

import androidx.lifecycle.LiveData
import com.example.financetracker.dao.TransactionDao
import com.example.financetracker.models.Transaction

class TransactionRepository(private val dao: TransactionDao) {
    val allTransactions: LiveData<List<Transaction>> = dao.getAllTransactions()

    suspend fun insert(transaction: Transaction) {
        dao.insertTransaction(transaction)
    }

    suspend fun update(transaction: Transaction) {
        dao.updateTransaction(transaction)
    }

    suspend fun delete(transaction: Transaction) {
        dao.deleteTransaction(transaction)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }
}