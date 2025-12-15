package com.example.financetracker.repository

import com.example.financetracker.dao.BudgetDao
import com.example.financetracker.models.Budget
import kotlinx.coroutines.flow.Flow

class BudgetRepository(private val budgetDao: BudgetDao) {
    val allBudgets: Flow<List<Budget>> = budgetDao.getAllBudgets()

    suspend fun insert(budget: Budget) {
        budgetDao.insertBudget(budget)
    }

    // ✅ Updated to return Flow<Budget?>
    fun getBudget(key: String): Flow<Budget?> {
        return budgetDao.getBudgetByKeyFlow(key)
    }

    suspend fun updateBudget(budget: Budget) {
        budgetDao.updateBudget(budget)
    }

    suspend fun deleteAllBudgets() {
        budgetDao.deleteAll()
    }
}
