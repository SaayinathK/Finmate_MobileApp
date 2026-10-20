package com.example.financetracker

import com.example.financetracker.models.DailyTask
import com.example.financetracker.models.TaskCompletion
import com.example.financetracker.models.Transaction
import com.example.financetracker.utils.PlannerRules
import com.example.financetracker.utils.TransactionSuggestions
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class PlannerUpgradeTest {
    private val today = LocalDate.parse("2026-10-20")
    @Test fun subtaskReminderCanPrecedeItsOwnDeadline() {
        val task = DailyTask(id = 2, title = "Submit documents", parentTaskId = 1, startDate = "2026-11-01", deadlineTime = "17:00", reminderDate = "2026-10-25", reminderTime = "08:15")
        assertEquals(LocalDateTime.parse("2026-10-25T08:15"), PlannerRules.nextReminder(task, today.atStartOfDay()))
        assertNull(PlannerRules.nextReminder(task, LocalDateTime.parse("2026-10-25T08:15")))
        assertFalse(PlannerRules.occurs(task, today))
        assertTrue(PlannerRules.occurs(task, LocalDate.parse("2026-11-01")))
    }
    @Test fun onAndAfterContinuesEveryDayAcrossDeadline() {
        val task = DailyTask(id = 1, title = "Renew", startDate = "2026-10-19", reminderTime = "09:00", remindUntilDone = true)
        assertEquals(LocalDateTime.parse("2026-10-21T09:00"), PlannerRules.nextReminder(task, LocalDateTime.parse("2026-10-20T10:00")))
        assertTrue(PlannerRules.occurs(task, today))
        val done = listOf(TaskCompletion(taskId = 1, occurrenceDate = "2026-10-19", title = "Renew", notes = ""))
        assertTrue(PlannerRules.isCompleted(task, today, done))
        assertTrue(PlannerRules.isCompleted(task, today.plusDays(90), done))
        assertEquals("2026-10-19", PlannerRules.completionDate(task, today))
        assertFalse(PlannerRules.isCompleted(task.copy(id = 2), today, done))
        assertNull(PlannerRules.nextReminder(task.copy(active = false), today.atStartOfDay()))
    }
    @Test fun upcomingOnAndAfterReminderDoesNotFireEarly() {
        val task = DailyTask(title = "Review", startDate = "2026-11-01", reminderDate = "2026-10-30", reminderTime = "15:30", remindUntilDone = true)
        assertEquals(LocalDateTime.parse("2026-10-30T15:30"), PlannerRules.nextReminder(task, today.atStartOfDay()))
        assertEquals(LocalDateTime.parse("2026-10-31T15:30"), PlannerRules.nextReminder(task, LocalDateTime.parse("2026-10-30T15:30")))
    }
    @Test fun completedOverdueTaskLeavesLaterChecklistsButKeepsHistory() {
        val task = DailyTask(id = 3, title = "Renew", startDate = "2026-10-19", remindUntilDone = true)
        val finished = today.atTime(10, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val completion = listOf(TaskCompletion(taskId = 3, occurrenceDate = task.startDate, completedAt = finished, title = task.title, notes = ""))
        assertTrue(PlannerRules.occurs(task, today, completion))
        assertFalse(PlannerRules.occurs(task, today.plusDays(1), completion))
        assertTrue(PlannerRules.isCompleted(task, today.plusDays(1), completion))
    }
    @Test fun suggestionsUseRepeatedHistoryAndKeepIncomeSeparate() {
        fun entry(id: Int, day: String, amount: Double = -500.0) = Transaction(id = id, title = if (id == 1) " Bus pass " else "Bus pass", category = "Transport", amount = amount, type = if (amount < 0) "Expense" else "Income", date = day)
        val history = listOf(entry(1,"2026-10-01"), entry(2,"2026-10-08"), entry(3,"2026-10-15",-700.0), entry(4,"2026-10-20",500.0))
        val suggestion = TransactionSuggestions.from(history, today, "Expense").single()
        assertEquals("Bus pass", suggestion.title); assertEquals("Weekly", suggestion.cadence)
        assertEquals(500.0, suggestion.amount, 0.0); assertEquals(3, suggestion.count)
        assertTrue(TransactionSuggestions.from(history, today, "Income").isEmpty())
    }
    @Test fun suggestionsExcludeSplitsLoansInvalidAndFutureEntries() {
        val entry = Transaction(title = "Rent", amount = -2000.0, category = "Housing", date = "2026-10-01", type = "Expense")
        val history = listOf(entry, entry.copy(loanId = 1), entry.copy(splitGroup = "bill:1"), entry.copy(date = "bad date"), entry.copy(date = "2027-01-01"), entry.copy(amount = Double.NaN))
        assertTrue(TransactionSuggestions.from(history, today, "Expense").isEmpty())
    }
}
