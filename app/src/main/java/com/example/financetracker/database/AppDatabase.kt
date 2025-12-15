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

@Database(
    entities = [Transaction::class, UserProfile::class, Budget::class, Loan::class],
    version = 5, // ⬆️ Incremented due to model changes
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun budgetDao(): BudgetDao

    abstract fun loanDao(): LoanDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finance_tracker_db"
                )
                    .fallbackToDestructiveMigration() // ⚠️ Wipes old DB if migration fails
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
