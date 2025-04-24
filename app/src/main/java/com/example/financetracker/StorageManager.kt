package com.example.financetracker

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object StorageManager {
    private const val PREF_NAME = "FinMatePrefs"
    private const val KEY_TRANSACTIONS = "transactions"
    private val gson = Gson()

    fun saveTransaction(context: Context, transaction: Transaction) {
        val list = getTransactions(context).toMutableList()
        list.add(transaction)
        val json = gson.toJson(list)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_TRANSACTIONS, json).apply()
    }

    fun getTransactions(context: Context): List<Transaction> {
        val json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TRANSACTIONS, "[]")
        val type = object : TypeToken<List<Transaction>>() {}.type
        return gson.fromJson(json, type)
    }

    fun deleteTransaction(context: Context, index: Int) {
        val list = getTransactions(context).toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            val json = gson.toJson(list)
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_TRANSACTIONS, json).apply()
        }
    }
}
