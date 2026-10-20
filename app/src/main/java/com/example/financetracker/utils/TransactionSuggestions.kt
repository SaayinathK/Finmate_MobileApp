package com.example.financetracker.utils

import com.example.financetracker.models.Transaction
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

data class TransactionSuggestion(val title: String, val category: String, val amount: Double, val type: String, val count: Int, val cadence: String)

/** Local history only. Suggestions fill editable fields; they never create an entry. */
object TransactionSuggestions {
    fun from(history: List<Transaction>, today: LocalDate, type: String): List<TransactionSuggestion> = history
        .filter { it.type == type && it.loanId == null && it.splitGroup == null && it.amount.isFinite() && abs(it.amount) > 0 && PlannerRules.parseDate(it.date)?.let { day -> day <= today } == true }
        .groupBy { it.title.trim().lowercase(Locale.ROOT) to it.category.trim().lowercase(Locale.ROOT) }
        .filter { it.key.first.isNotBlank() && it.value.size >= 2 }
        .map { (_, entries) ->
            val sorted = entries.sortedWith(compareByDescending<Transaction> { PlannerRules.parseDate(it.date) }.thenByDescending { it.id })
            val last = sorted.first()
            val dates = entries.mapNotNull { PlannerRules.parseDate(it.date) }.distinct().sorted()
            val intervals = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }.sorted()
            val interval = intervals.getOrNull(intervals.size / 2)
            val cadence = when (interval) { 1L -> "Daily"; in 6L..8L -> "Weekly"; in 13L..15L -> "Every two weeks"; in 27L..32L -> "Monthly"; else -> "Frequent" }
            val amounts = entries.map { abs(it.amount) }.sorted()
            TransactionSuggestion(last.title.trim(), last.category.trim(), amounts[amounts.size / 2], type, entries.size, cadence) to PlannerRules.parseDate(last.date)!!
        }
        .sortedWith(compareByDescending<Pair<TransactionSuggestion, LocalDate>> { it.first.count }.thenByDescending { it.second })
        .take(5).map { it.first }
}
