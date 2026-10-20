package com.example.financetracker.utils

import android.content.Context

/** Keep custom categories even after their last transaction is removed. */
object CategoryStore {
    private val defaults = listOf("Food", "Transport", "Bills", "Salary", "Other", "Loans")
    fun all(context: Context, used: List<String> = emptyList()): List<String> =
        (defaults + context.getSharedPreferences("finmate_settings", Context.MODE_PRIVATE)
            .getStringSet("categories", emptySet()).orEmpty().sorted() + used)
            .map(String::trim).filter(String::isNotEmpty).distinctBy { it.lowercase(java.util.Locale.ROOT) }
    @Synchronized fun remember(context: Context, names: List<String>) {
        val prefs = context.getSharedPreferences("finmate_settings", Context.MODE_PRIVATE)
        val existing = all(context)
        val additions = names.map(String::trim).filter { name -> name.isNotEmpty() && existing.none { it.equals(name, true) } }
        prefs.edit().putStringSet("categories", prefs.getStringSet("categories", emptySet()).orEmpty().toSet() + additions).apply()
    }
}
