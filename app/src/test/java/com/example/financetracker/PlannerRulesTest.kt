package com.example.financetracker

import com.example.financetracker.models.DailyTask
import com.example.financetracker.utils.PlannerRules
import com.example.financetracker.utils.ReportPeriod
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class PlannerRulesTest {
    private fun date(value: String) = LocalDate.parse(value)
    @Test fun weekCrossesYearAndMonthBoundaries() {
        assertEquals(date("2025-12-29") to date("2026-01-04"), PlannerRules.bounds(date("2026-01-01"), ReportPeriod.WEEK))
        assertEquals(date("2024-02-01") to date("2024-02-29"), PlannerRules.bounds(date("2024-02-22"), ReportPeriod.MONTH))
    }
    @Test fun selectedDaysRespectStartDate() {
        val task = DailyTask(title = "Review", startDate = "2026-10-20", recurrence = "WEEKLY", weekdays = "1,3,5")
        assertFalse(PlannerRules.occurs(task, date("2026-10-19")))
        assertTrue(PlannerRules.occurs(task, date("2026-10-21")))
        assertFalse(PlannerRules.occurs(task, date("2026-10-22")))
    }
    @Test fun repeatingCompletionDoesNotStopFutureOccurrences() {
        val task = DailyTask(title = "Read", startDate = "2026-10-19", recurrence = "DAILY")
        assertTrue(PlannerRules.occurs(task, date("2026-10-19")))
        assertTrue(PlannerRules.occurs(task, date("2026-10-20")))
        assertFalse(PlannerRules.occurs(task.copy(active = false), date("2026-10-20")))
    }
    @Test fun reminderIsAlwaysInFutureAndRespectsWeekdays() {
        val task = DailyTask(title = "Friday", startDate = "2026-10-19", recurrence = "WEEKLY", weekdays = "5", reminderTime = "09:30")
        assertEquals(LocalDateTime.parse("2026-10-23T09:30"), PlannerRules.nextReminder(task, LocalDateTime.parse("2026-10-19T22:00")))
        assertEquals(LocalDateTime.parse("2026-10-30T09:30"), PlannerRules.nextReminder(task, LocalDateTime.parse("2026-10-23T09:30")))
        assertNull(PlannerRules.nextReminder(task.copy(reminderEnabled = false), LocalDateTime.parse("2026-10-19T22:00")))
        assertNull(PlannerRules.nextReminder(task.copy(recurrence = "ONCE"), LocalDateTime.parse("2026-10-20T22:00")))
    }
    @Test fun futureStartCanBeBeyondNextWeek() {
        val task = DailyTask(title = "Later", startDate = "2027-01-04", recurrence = "DAILY")
        assertEquals(LocalDateTime.parse("2027-01-04T09:00"), PlannerRules.nextReminder(task, LocalDateTime.parse("2026-10-19T12:00")))
    }
    @Test fun moneyRejectsInvalidPrecisionAndNonFiniteValues() {
        assertEquals(125050L, PlannerRules.cents("1250.50"))
        listOf("0", "-1", "NaN", "Infinity", "0.001", "999999999999999999999999").forEach { assertNull(PlannerRules.cents(it)) }
    }
    @Test fun splittingNeverLosesACent() {
        val parts = PlannerRules.splitEqual(10000, 3)
        assertEquals(listOf(3334L,3333L,3333L), parts)
        assertEquals(10000L, parts.sum())
    }
    @Test fun partialAndFinalSettlements() {
        assertEquals(750.0, PlannerRules.remaining(1000.0,250.0), 0.0)
        assertEquals(0.0, PlannerRules.remaining(750.0,750.0), 0.0)
    }
    @Test(expected = IllegalArgumentException::class) fun cannotOverpay() { PlannerRules.remaining(100.0,100.01) }
    @Test(expected = IllegalArgumentException::class) fun cannotPayNegative() { PlannerRules.remaining(100.0,-1.0) }
    @Test fun legacyDatesRemainReadable() {
        assertEquals(date("2026-10-19"), PlannerRules.parseDate("19 Oct 2026"))
        assertEquals(date("2026-10-19"), PlannerRules.parseDate("2026-10-19"))
        assertNull(PlannerRules.parseDate("not a date"))
        assertNull(PlannerRules.parseDate("30 Feb 2026"))
    }
}
