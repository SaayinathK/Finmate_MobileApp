package com.example.financetracker.repository

import androidx.room.withTransaction
import com.example.financetracker.database.AppDatabase

object DataMaintenance {
    // Remove linked loans and history together; genuinely unlinked legacy loans remain.
    suspend fun deleteTransactions(db: AppDatabase) = db.withTransaction { db.transactionDao().deleteAll() }
    suspend fun deleteTask(db: AppDatabase, id: Int) = db.withTransaction {
        db.taskDao().children(id).forEach { child ->
            db.taskDao().deleteHistory(child.id)
            db.taskDao().deleteTask(child.id)
        }
        db.taskDao().deleteHistory(id)
        db.taskDao().deleteTask(id)
    }
    suspend fun deleteTasks(db: AppDatabase) = db.withTransaction {
        db.taskDao().deleteAllHistory()
        db.taskDao().deleteAllTasks()
    }
}
