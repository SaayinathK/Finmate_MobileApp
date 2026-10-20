package com.example.financetracker.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "loans")
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val personName: String,
    val amount: Double,
    val type: String,   // "RECEIVED" (income) or "GIVVEN" (expense)
    val note: String? = null,
    val date: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val originalAmount: Double = amount,
    val sharedBillGroup: String? = null
) : java.io.Serializable
