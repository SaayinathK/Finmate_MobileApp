package com.example.financetracker.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "loans")
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val personName: String,
    val amount: Double,
    val type: String,   // "RECEIVED" (income) or "GIVVEN" (expense)
    val note: String? = null,
    val date: Long = System.currentTimeMillis()
) : java.io.Serializable
