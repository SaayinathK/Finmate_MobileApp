package com.example.financetracker.utils

import com.example.financetracker.models.DailyTask
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

enum class ReportPeriod(val label: String) { DAY("Daily"), WEEK("Weekly"), MONTH("Monthly") }

object PlannerRules {
    private val storedDate = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
    fun parseDate(value: String): LocalDate? {
        for (formatter in listOf(storedDate, DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.getDefault()).withResolverStyle(ResolverStyle.STRICT), DateTimeFormatter.ISO_LOCAL_DATE)) {
            try { return LocalDate.parse(value, formatter) } catch (_: Exception) { }
        }
        return null
    }
    fun displayDate(date: LocalDate): String = date.format(storedDate)
    fun bounds(anchor: LocalDate, period: ReportPeriod): Pair<LocalDate, LocalDate> = when (period) {
        ReportPeriod.DAY -> anchor to anchor
        ReportPeriod.WEEK -> anchor.with(DayOfWeek.MONDAY).let { it to it.plusDays(6) }
        ReportPeriod.MONTH -> anchor.withDayOfMonth(1) to anchor.withDayOfMonth(anchor.lengthOfMonth())
    }
    fun occurs(task: DailyTask, date: LocalDate): Boolean {
        val start = LocalDate.parse(task.startDate)
        if (!task.active || date < start) return false
        return when (task.recurrence) {
            "DAILY" -> true
            "WEEKLY" -> date.dayOfWeek.value.toString() in task.weekdays.split(",")
            else -> date == start
        }
    }
    fun nextReminder(task: DailyTask, now: LocalDateTime): LocalDateTime? {
        if (!task.active || !task.reminderEnabled) return null
        val start = maxOf(now.toLocalDate(), LocalDate.parse(task.startDate))
        for (offset in 0L..7L) {
            val date = start.plusDays(offset)
            val candidate = date.atTime(LocalTime.parse(task.reminderTime))
            if (occurs(task, date) && candidate > now) return candidate
        }
        return null
    }
    fun cents(amount: String): Long? = try {
        BigDecimal(amount.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact().takeIf { it > 0 }
    } catch (_: Exception) { null }
    fun splitEqual(totalCents: Long, count: Int): List<Long> {
        require(totalCents > 0 && count > 0 && totalCents >= count)
        return List(count) { totalCents / count + if (it < totalCents % count) 1 else 0 }
    }
    fun remaining(outstanding: Double, payment: Double): Double {
        require(payment.isFinite() && payment > 0 && payment <= outstanding + 0.000001) { "Enter an amount up to the outstanding balance" }
        return BigDecimal.valueOf(outstanding).subtract(BigDecimal.valueOf(payment)).setScale(2, RoundingMode.HALF_UP).toDouble().coerceAtLeast(0.0)
    }
}
