package com.example.financetracker.utils

import com.example.financetracker.models.DailyTask
import com.example.financetracker.models.TaskCompletion
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
    fun taskSchedule(task: DailyTask): String = when (task.recurrence) {
        "DAILY" -> "Daily"
        "WEEKLY" -> task.weekdays.split(",").mapNotNull { it.toIntOrNull()?.takeIf { day -> day in 1..7 } }
            .sorted().joinToString(", ") { DayOfWeek.of(it).getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) }
        else -> displayDate(LocalDate.parse(task.startDate))
    }
    fun taskReminder(task: DailyTask): String {
        val deadline = "${taskSchedule(task)} · Due ${task.deadlineTime}"
        if (!task.reminderEnabled) return "$deadline · Reminder off"
        val day = task.reminderDate?.let { displayDate(LocalDate.parse(it)) } ?: taskSchedule(task)
        return "$deadline · Remind $day ${task.reminderTime}${if (task.remindUntilDone && task.recurrence == "ONCE") ", daily until done" else ""}"
    }
    // A one-off task has one completion, including when it remains visible after its deadline.
    fun completionDate(task: DailyTask, date: LocalDate): String = if (task.recurrence == "ONCE") task.startDate else date.toString()
    fun isCompleted(task: DailyTask, date: LocalDate, completed: List<TaskCompletion>): Boolean =
        completed.any { it.taskId == task.id && it.occurrenceDate == completionDate(task, date) }
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
            else -> date == start || (task.remindUntilDone && date > start)
        }
    }
    fun occurs(task: DailyTask, date: LocalDate, completed: List<TaskCompletion>): Boolean {
        if (!occurs(task, date)) return false
        if (task.recurrence != "ONCE" || !task.remindUntilDone) return true
        val completion = completed.firstOrNull { it.taskId == task.id && it.occurrenceDate == task.startDate } ?: return true
        val finished = java.time.Instant.ofEpochMilli(completion.completedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        return date <= maxOf(LocalDate.parse(task.startDate), finished)
    }
    fun nextReminder(task: DailyTask, now: LocalDateTime): LocalDateTime? {
        if (!task.active || !task.reminderEnabled) return null
        if (task.recurrence == "ONCE") {
            val first = LocalDate.parse(task.reminderDate ?: task.startDate).atTime(LocalTime.parse(task.reminderTime))
            if (first > now) return first
            if (!task.remindUntilDone) return null
            val today = now.toLocalDate().atTime(LocalTime.parse(task.reminderTime))
            return if (today > now) today else today.plusDays(1)
        }
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
