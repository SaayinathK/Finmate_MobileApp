package com.example.financetracker.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.financetracker.models.Budget
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: Budget)

    @Update
    suspend fun updateBudget(budget: Budget)

    @Query("SELECT * FROM budget WHERE budgetKey = :budgetKey LIMIT 1")
    fun getBudgetByKeyFlow(budgetKey: String): Flow<Budget?>

    @Query("SELECT * FROM budget ORDER BY budgetKey ASC")
    fun getAllBudgets(): Flow<List<Budget>>

    @Query("SELECT * FROM budget WHERE budgetKey = :budgetKey LIMIT 1")
    suspend fun getBudgetByKey(budgetKey: String): Budget?

    // ✅ Directly Update Currency for Specific Budget Key
    @Query("UPDATE budget SET currency = :currency WHERE budgetKey = :budgetKey")
    suspend fun updateCurrency(budgetKey: String, currency: String)

    // ✅ Directly get Currency by Budget Key
    @Query("SELECT currency FROM budget WHERE budgetKey = :budgetKey LIMIT 1")
    suspend fun getCurrencyByBudgetKey(budgetKey: String): String?

    @Query("DELETE FROM budget")
    suspend fun deleteAll()
}
