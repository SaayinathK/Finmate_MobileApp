package com.example.financetracker.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.room.withTransaction
import com.example.financetracker.R
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.DailyTask
import com.example.financetracker.utils.PlannerRules
import com.example.financetracker.utils.WidgetTaskRows
import android.appwidget.AppWidgetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/** Collection fallback for Android 8–11. Android 12+ receives collection items directly. */
class TodayTasksService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = TodayTasksFactory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
}

class TodayTasksFactory(private val context: Context, private val widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID) : RemoteViewsService.RemoteViewsFactory {
    private var snapshot = TodayTasksSnapshot(LocalDate.now(), emptyList(), emptySet(), false)
    override fun onCreate() = Unit
    override fun onDataSetChanged() { snapshot = runBlocking(Dispatchers.IO) { TodayTasksSnapshot.load(context, widgetId) } }
    override fun onDestroy() = Unit
    override fun getCount() = snapshot.tasks.size
    override fun getViewAt(position: Int): RemoteViews? = snapshot.tasks.getOrNull(position)?.let { snapshot.row(context, it) }
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = snapshot.tasks.getOrNull(position)?.id?.toLong() ?: -1L
    override fun hasStableIds() = true
}

internal data class TodayTasksSnapshot(val date: LocalDate, val tasks: List<DailyTask>, val done: Set<Int>, val signedIn: Boolean,
    val dueIds: Set<Int> = tasks.map { it.id }.toSet(), val childCounts: Map<Int, Int> = emptyMap(),
    val expanded: Set<Int> = emptySet(), val widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID) {
    fun row(context: Context, task: DailyTask): RemoteViews {
        val dark = isDark(context)
        val completed = task.id in done
        val ink = android.graphics.Color.parseColor(if (dark) "#EDF3FC" else "#182B48")
        val muted = android.graphics.Color.parseColor(if (dark) "#A6B6CA" else "#677990")
        val count = childCounts[task.id] ?: 0
        val canComplete = task.id in dueIds
        val isExpanded = task.id in expanded
        fun click(operation: String) = Intent().putExtra("taskId", task.id).putExtra("date", date.toString())
            .putExtra("complete", !completed).putExtra("operation", operation).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        return RemoteViews(context.packageName, R.layout.widget_task_row).apply {
            setTextViewText(R.id.widgetTaskTitle, (if (task.parentTaskId != null) "↳ " else "") + task.title)
            val deadline = if (task.recurrence == "ONCE") "Due ${PlannerRules.displayDate(LocalDate.parse(task.startDate))} ${task.deadlineTime}" else "${PlannerRules.taskSchedule(task)} · Due ${task.deadlineTime}"
            val reminderDay = if (task.recurrence == "ONCE") PlannerRules.displayDate(LocalDate.parse(task.reminderDate ?: task.startDate)) + " " else ""
            val reminder = if (task.reminderEnabled) "Remind $reminderDay${task.reminderTime}" else "Reminder off"
            setTextViewText(R.id.widgetTaskTime, "$deadline · $reminder" + if (count > 0) " · $count subtasks" else "")
            setTextViewText(R.id.widgetTaskCheck, if (completed) "☑" else if (canComplete) "☐" else "–")
            setBoolean(R.id.widgetTaskCheck, "setEnabled", canComplete)
            setTextColor(R.id.widgetTaskTitle, if (completed) muted else ink)
            setTextColor(R.id.widgetTaskTime, muted); setTextColor(R.id.widgetTaskCheck, if (completed) muted else ink)
            setInt(R.id.widgetTaskRow, "setBackgroundResource", if (dark) R.drawable.widget_row_dark else R.drawable.widget_row_light)
            setViewPadding(R.id.widgetTaskRow, ((if (task.parentTaskId == null) 8 else 20) * context.resources.displayMetrics.density).toInt(),
                (8 * context.resources.displayMetrics.density).toInt(), (8 * context.resources.displayMetrics.density).toInt(), (8 * context.resources.displayMetrics.density).toInt())
            val bodyClick = click(if (count > 0) "expand" else "complete")
            setOnClickFillInIntent(R.id.widgetTaskRow, bodyClick)
            setOnClickFillInIntent(R.id.widgetTaskContent, bodyClick)
            setOnClickFillInIntent(R.id.widgetTaskCheck, click("complete"))
            setViewVisibility(R.id.widgetTaskExpand, if (count > 0) android.view.View.VISIBLE else android.view.View.GONE)
            setTextViewText(R.id.widgetTaskExpand, if (isExpanded) "▾" else "▸")
            setTextColor(R.id.widgetTaskExpand, ink)
            setOnClickFillInIntent(R.id.widgetTaskExpand, click("expand"))
            setContentDescription(R.id.widgetTaskExpand, "${if (isExpanded) "Hide" else "Show"} $count subtasks for ${task.title}")
            setContentDescription(R.id.widgetTaskCheck, if (canComplete) "${if (completed) "Mark incomplete" else "Complete"} ${task.title}" else "${task.title} is not due today")
            setContentDescription(R.id.widgetTaskContent, if (count > 0) "${if (isExpanded) "Hide" else "Show"} subtasks for ${task.title}" else "${task.title}. $deadline. $reminder")
        }
    }
    companion object {
        fun isDark(context: Context): Boolean = when (context.getSharedPreferences("finmate_settings", Context.MODE_PRIVATE).getString("theme", "system")) {
            "dark" -> true; "light" -> false
            else -> context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        suspend fun load(context: Context, widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID): TodayTasksSnapshot {
            val db = AppDatabase.getDatabase(context)
            return db.withTransaction {
                val today = LocalDate.now()
                val signedIn = db.userProfileDao().getLoggedInUser() != null
                val completions = if (signedIn) db.taskDao().completions() else emptyList()
                val all = if (signedIn) db.taskDao().allTasks() else emptyList()
                val expanded = context.getSharedPreferences("swallet_widget", Context.MODE_PRIVATE).getStringSet("expanded_$widgetId", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
                val list = WidgetTaskRows.build(all, completions, today, expanded)
                TodayTasksSnapshot(today, list.rows, list.doneIds, signedIn, list.dueIds, list.childCounts, expanded, widgetId)
            }
        }
    }
}
