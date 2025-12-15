package com.example.financetracker.models

data class AccountSummary(
    val personName: String,
    val totalReceive: Double,
    val totalPay: Double
) {
    val net: Double get() = totalReceive - totalPay
}
