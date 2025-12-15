package com.example.financetracker.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budget")
data class Budget(
    @PrimaryKey val budgetKey: String, // Format: "2025_April"
    val amount: Double,
    val currency: String
)
