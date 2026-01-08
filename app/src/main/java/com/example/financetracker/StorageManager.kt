package com.example.financetracker

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object StorageManager {

    private const val PREF_NAME = "FinMatePrefs"
    private const val KEY_TRANSACTIONS = "transactions"
    private val gson = Gson()

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveTransaction(context: Context, transaction: Transaction) {
        val transactions = getTransactions(context).toMutableList()
        transactions.add(transaction)
        saveTransactions(context, transactions)
    }

    fun getTransactions(context: Context): List<Transaction> {
        val json = getPrefs(context).getString(KEY_TRANSACTIONS, "[]")
        val type = object : TypeToken<List<Transaction>>() {}.type
        return gson.fromJson(json, type)
    }

    fun deleteTransaction(context: Context, index: Int) {
        val transactions = getTransactions(context).toMutableList()
        if (index in transactions.indices) {
            transactions.removeAt(index)
            saveTransactions(context, transactions)
        }
    }

    fun updateTransaction(context: Context, index: Int, updatedTransaction: Transaction) {
        val transactions = getTransactions(context).toMutableList()
        if (index in transactions.indices) {
            transactions[index] = updatedTransaction
            saveTransactions(context, transactions)
        }
    }

    fun saveTransactions(context: Context, transactions: List<Transaction>) {
        val json = gson.toJson(transactions)
        getPrefs(context).edit().putString(KEY_TRANSACTIONS, json).apply()
    }
}
