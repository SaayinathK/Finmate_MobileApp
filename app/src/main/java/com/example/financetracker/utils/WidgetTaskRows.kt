package com.example.financetracker.utils

import com.example.financetracker.models.DailyTask
import com.example.financetracker.models.TaskCompletion
import java.time.LocalDate

data class WidgetTaskList(
    val rows: List<DailyTask>, val dueIds: Set<Int>, val doneIds: Set<Int>, val childCounts: Map<Int, Int>
)

/** Keep due subtasks under their parent, and include future subtasks only when expanded. */
object WidgetTaskRows {
    fun build(tasks: List<DailyTask>, completions: List<TaskCompletion>, today: LocalDate, expanded: Set<Int>): WidgetTaskList {
        val byId = tasks.associateBy { it.id }
        val active = tasks.filter { it.active && (it.parentTaskId == null || byId[it.parentTaskId]?.active != false) }
        val children = active.filter { it.parentTaskId != null && byId.containsKey(it.parentTaskId) }.groupBy { it.parentTaskId!! }
        val due = active.filter { PlannerRules.occurs(it, today, completions) }.map { it.id }.toSet()
        val done = active.filter { PlannerRules.isCompleted(it, today, completions) }.map { it.id }.toSet()
        val roots = active.filter { (it.parentTaskId == null || !byId.containsKey(it.parentTaskId)) &&
            (it.id in due || children[it.id].orEmpty().any { child -> child.id in due }) }
            .sortedWith(compareBy<DailyTask> { it.id in done }.thenBy { it.reminderTime }.thenBy { it.id })
        val rows = roots.flatMap { parent -> listOf(parent) + if (parent.id in expanded)
            children[parent.id].orEmpty().sortedWith(compareBy<DailyTask> { it.startDate }.thenBy { it.deadlineTime }.thenBy { it.id }) else emptyList() }
        return WidgetTaskList(rows, due, done, children.mapValues { it.value.size })
    }
}
