package com.example.financetracker.utils

import android.content.Context
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StorageManager {

    suspend fun saveTransaction(context: Context, transaction: Transaction) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            db.transactionDao().insertTransaction(transaction)
        }
    }

    suspend fun getTransactions(context: Context): List<Transaction> {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            db.transactionDao().getAllTransactionsRaw()
        }
    }

    suspend fun deleteTransaction(context: Context, transaction: Transaction) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            db.transactionDao().deleteTransaction(transaction)
        }
    }
}
