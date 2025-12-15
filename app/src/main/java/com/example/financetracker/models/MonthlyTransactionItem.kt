package com.example.financetracker.models

sealed class MonthlyTransactionItem {
    data class Header(val month: String) : MonthlyTransactionItem()
    data class TransactionItem(val transaction: Transaction) : MonthlyTransactionItem()
}
