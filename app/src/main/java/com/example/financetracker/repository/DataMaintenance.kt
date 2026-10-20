package com.example.financetracker.repository

import androidx.room.withTransaction
import com.example.financetracker.database.AppDatabase

object DataMaintenance {
    // Keep outstanding person balances when clearing transaction history. Future settlements still work.
    suspend fun deleteTransactions(db: AppDatabase) = db.withTransaction { db.transactionDao().deleteAll() }
    suspend fun deleteTask(db: AppDatabase, id: Int) = db.withTransaction {
        db.taskDao().deleteHistory(id)
        db.taskDao().deleteTask(id)
    }
    suspend fun deleteTasks(db: AppDatabase) = db.withTransaction {
        db.taskDao().deleteAllHistory()
        db.taskDao().deleteAllTasks()
    }
}
