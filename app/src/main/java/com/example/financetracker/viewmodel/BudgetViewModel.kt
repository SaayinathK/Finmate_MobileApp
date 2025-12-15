package com.example.financetracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.Budget
import com.example.financetracker.repository.BudgetRepository
import kotlinx.coroutines.launch

class BudgetViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: BudgetRepository

    init {
        val dao = AppDatabase.getDatabase(application).budgetDao()
        repository = BudgetRepository(dao)
    }

    val allBudgets: LiveData<List<Budget>> = repository.allBudgets.asLiveData()

    fun insert(budget: Budget) = viewModelScope.launch {
        repository.insert(budget)
    }

    fun getBudget(key: String): LiveData<Budget?> = repository.getBudget(key).asLiveData()

    fun deleteAllBudgets() = viewModelScope.launch {
        repository.deleteAllBudgets()
    }
}
