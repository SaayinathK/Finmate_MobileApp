package com.example.financetracker.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val amount: Double,
    val category: String,
    val date: String,
    val type: String,
    val loanId: Int? = null,
    val loanEvent: String? = null,
    val splitGroup: String? = null
)
