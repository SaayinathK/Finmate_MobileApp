package com.example.financetracker.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.financetracker.dao.BudgetDao
import com.example.financetracker.dao.LoanDao
import com.example.financetracker.dao.TransactionDao
import com.example.financetracker.dao.UserProfileDao
import com.example.financetracker.models.Budget
import com.example.financetracker.models.Loan
import com.example.financetracker.models.Transaction
import com.example.financetracker.models.UserProfile
import com.example.financetracker.models.DailyTask
import com.example.financetracker.models.TaskCompletion
import com.example.financetracker.dao.TaskDao
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Transaction::class, UserProfile::class, Budget::class, Loan::class, DailyTask::class, TaskCompletion::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun budgetDao(): BudgetDao

    abstract fun loanDao(): LoanDao
    abstract fun taskDao(): TaskDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN loanId INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN loanEvent TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN splitGroup TEXT")
                db.execSQL("ALTER TABLE loans ADD COLUMN originalAmount REAL NOT NULL DEFAULT 0")
                db.execSQL("UPDATE loans SET originalAmount = amount")
                db.execSQL("CREATE TABLE IF NOT EXISTS daily_tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, notes TEXT NOT NULL, startDate TEXT NOT NULL, recurrence TEXT NOT NULL, weekdays TEXT NOT NULL, reminderTime TEXT NOT NULL, reminderEnabled INTEGER NOT NULL, active INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS task_completions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskId INTEGER NOT NULL, occurrenceDate TEXT NOT NULL, completedAt INTEGER NOT NULL, title TEXT NOT NULL, notes TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_task_completions_taskId_occurrenceDate ON task_completions (taskId, occurrenceDate)")
            }
        }
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finance_tracker_db"
                )
                    .addMigrations(MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
