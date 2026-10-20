package com.example.financetracker

import com.example.financetracker.models.DailyTask
import com.example.financetracker.models.TaskCompletion
import com.example.financetracker.utils.WidgetTaskRows
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WidgetTaskRowsTest {
    private val today = LocalDate.parse("2026-10-20")
    private val parent = DailyTask(id = 1, title = "Project", startDate = today.toString())
    private val dueChild = DailyTask(id = 2, title = "Outline", parentTaskId = 1, startDate = today.toString())
    private val futureChild = DailyTask(id = 3, title = "Submit", parentTaskId = 1, startDate = today.plusDays(7).toString())
    @Test fun collapsedParentHidesChildrenButCountsAllDueWork() {
        val result = WidgetTaskRows.build(listOf(parent,dueChild,futureChild), emptyList(), today, emptySet())
        assertEquals(listOf(1), result.rows.map { it.id })
        assertEquals(setOf(1,2), result.dueIds)
        assertEquals(2, result.childCounts[1])
    }
    @Test fun expansionRevealsFutureChildrenWithoutChangingDueCount() {
        val result = WidgetTaskRows.build(listOf(futureChild,dueChild,parent), emptyList(), today, setOf(1))
        assertEquals(listOf(1,2,3), result.rows.map { it.id })
        assertEquals(setOf(1,2), result.dueIds)
        assertFalse(3 in result.dueIds)
    }
    @Test fun dueChildMakesFutureParentAvailableForExpansion() {
        val result = WidgetTaskRows.build(listOf(parent.copy(startDate = today.plusDays(9).toString()),dueChild), emptyList(), today, setOf(1))
        assertEquals(listOf(1,2), result.rows.map { it.id })
        assertEquals(setOf(2), result.dueIds)
    }
    @Test fun parentCompletionDoesNotCompleteItsChildren() {
        val complete = TaskCompletion(taskId = 1, occurrenceDate = today.toString(), title = parent.title, notes = "")
        val result = WidgetTaskRows.build(listOf(parent,dueChild), listOf(complete), today, setOf(1))
        assertEquals(setOf(1), result.doneIds)
        assertEquals(listOf(1,2), result.rows.map { it.id })
    }
    @Test fun archivedFamilyIsHiddenAndUnrelatedFutureTasksStayHidden() {
        val unrelated = DailyTask(id = 4, title = "Later", startDate = today.plusDays(2).toString())
        val result = WidgetTaskRows.build(listOf(parent.copy(active = false),dueChild,futureChild,unrelated), emptyList(), today, setOf(1,4))
        assertTrue(result.rows.isEmpty()); assertTrue(result.dueIds.isEmpty())
    }
    @Test fun separateWidgetsCanExpandDifferentParents() {
        val other = parent.copy(id = 4, title = "Other")
        val otherChild = dueChild.copy(id = 5, parentTaskId = 4)
        val tasks = listOf(parent,dueChild,other,otherChild)
        assertEquals(listOf(1,2,4), WidgetTaskRows.build(tasks, emptyList(), today, setOf(1)).rows.map { it.id })
        assertEquals(listOf(1,4,5), WidgetTaskRows.build(tasks, emptyList(), today, setOf(4)).rows.map { it.id })
    }
}
