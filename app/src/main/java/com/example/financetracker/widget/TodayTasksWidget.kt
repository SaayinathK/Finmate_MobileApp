package com.example.financetracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.room.withTransaction
import com.example.financetracker.R
import com.example.financetracker.activities.QuickEntryActivity
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.TaskCompletion
import com.example.financetracker.receiver.NotificationScheduler
import com.example.financetracker.utils.PlannerRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TodayTasksWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { refresh(context) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) { refresh(context) }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action !in setOf(REFRESH, TOGGLE)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == TOGGLE) {
                    val db = AppDatabase.getDatabase(context)
                    val date = intent.getStringExtra("date")
                    // Ignore yesterday's cached clicks and tasks removed since the last refresh.
                    if (date == LocalDate.now().toString() && db.userProfileDao().getLoggedInUser() != null) {
                        db.withTransaction {
                            val task = db.taskDao().task(intent.getIntExtra("taskId", -1))
                            if (task != null && PlannerRules.occurs(task, LocalDate.now())) {
                                if (intent.getBooleanExtra("complete", true)) db.taskDao().complete(TaskCompletion(
                                    taskId = task.id, occurrenceDate = date, title = task.title, notes = task.notes))
                                else db.taskDao().undo(task.id, date)
                            }
                        }
                        NotificationScheduler.rescheduleTasks(context)
                    }
                }
                updateAll(context)
            } catch (error: Exception) { android.util.Log.w("TodayTasksWidget", "Could not refresh tasks", error) }
            finally { pending.finish() }
        }
    }
    companion object {
        private const val REFRESH = "com.example.financetracker.widget.REFRESH"
        private const val TOGGLE = "com.example.financetracker.widget.TOGGLE"
        fun refresh(context: Context) { context.sendBroadcast(Intent(context, TodayTasksWidget::class.java).setAction(REFRESH)) }
        private fun launch(context: Context, kind: String, code: Int) = PendingIntent.getActivity(context, code,
            Intent(context, QuickEntryActivity::class.java).setAction("com.example.financetracker.quick.$kind")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        suspend fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayTasksWidget::class.java))
            if (ids.isEmpty()) return
            val db = AppDatabase.getDatabase(context)
            val signedIn = db.userProfileDao().getLoggedInUser() != null
            val today = LocalDate.now()
            val tasks = if (signedIn) db.taskDao().allTasks().filter { PlannerRules.occurs(it, today) } else emptyList()
            val done = if (signedIn) db.taskDao().completions().filter { it.occurrenceDate == today.toString() }.map { it.taskId }.toSet() else emptySet()
            val prefs = context.getSharedPreferences("finmate_settings", Context.MODE_PRIVATE)
            val dark = when (prefs.getString("theme", "system")) {
                "dark" -> true; "light" -> false
                else -> context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            val ink = android.graphics.Color.parseColor(if (dark) "#EDF3FC" else "#182B48")
            val muted = android.graphics.Color.parseColor(if (dark) "#A6B6CA" else "#677990")
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_today_tasks)
                views.setInt(R.id.widgetRoot, "setBackgroundResource", if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light)
                views.setTextColor(R.id.widgetTitle, ink); views.setTextColor(R.id.widgetSubtitle, muted)
                views.setTextViewText(R.id.widgetSubtitle, "${today.format(DateTimeFormatter.ofPattern("EEE, d MMM"))} · ${tasks.count { it.id in done }}/${tasks.size} done")
                views.setOnClickPendingIntent(R.id.widgetHeader, launch(context, "today", 900))
                views.setOnClickPendingIntent(R.id.widgetAdd, launch(context, "task", 901))
                views.removeAllViews(R.id.widgetRows)
                val availableHeight = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 250)
                val limit = ((availableHeight - 90) / 44).coerceIn(1, 5)
                val ordered = tasks.sortedBy { it.id in done }
                ordered.take(limit).forEach { task ->
                    val completed = task.id in done
                    val row = RemoteViews(context.packageName, R.layout.widget_task_row)
                    row.setTextViewText(R.id.widgetTaskTitle, "${if (completed) "☑" else "☐"}  ${task.title}")
                    row.setTextColor(R.id.widgetTaskTitle, if (completed) muted else ink)
                    row.setContentDescription(R.id.widgetTaskTitle, "${if (completed) "Mark incomplete" else "Complete"}: ${task.title}")
                    row.setOnClickPendingIntent(R.id.widgetTaskTitle, PendingIntent.getBroadcast(context, task.id,
                        Intent(context, TodayTasksWidget::class.java).setAction(TOGGLE).putExtra("taskId", task.id)
                            .putExtra("date", today.toString()).putExtra("complete", !completed),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    views.addView(R.id.widgetRows, row)
                }
                views.setTextColor(R.id.widgetFooter, muted)
                views.setTextViewText(R.id.widgetFooter, when {
                    !signedIn -> "Open SWallet to sign in"
                    tasks.isEmpty() -> "No tasks today · Tap + to add"
                    tasks.size > limit -> "+${tasks.size - limit} more · Open checklist"
                    else -> "Open checklist"
                })
                views.setOnClickPendingIntent(R.id.widgetFooter, launch(context, "today", 900))
                manager.updateAppWidget(id, views)
            }
        }
    }
}
