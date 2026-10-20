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
import com.example.financetracker.ui.WalletGuideView
import com.example.financetracker.receiver.NotificationScheduler
import com.example.financetracker.utils.PlannerRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TodayTasksWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { refresh(context) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) { refresh(context) }
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = context.getSharedPreferences("swallet_widget", Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { editor.remove("expanded_$it") }; editor.apply()
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action !in setOf(REFRESH, TOGGLE)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                interactions.withLock {
                    if (intent.action == TOGGLE) handleInteraction(context, intent)
                    updateAll(context)
                }
            } catch (error: Exception) { android.util.Log.w("TodayTasksWidget", "Could not refresh tasks", error) }
            finally { pending.finish() }
        }
    }
    private suspend fun handleInteraction(context: Context, intent: Intent) {
        // Details and subtask expansion stay visible; changing completion requires an app unlock.
        if (intent.getStringExtra("operation") != "expand" && com.example.financetracker.security.AppLock.isLocked(context)) {
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Unlock SWallet before changing tasks", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
        val today = LocalDate.now()
        val db = AppDatabase.getDatabase(context)
        // Ignore stale cached clicks, removed tasks, and signed-out widget sessions.
        if (intent.getStringExtra("date") != today.toString() || db.userProfileDao().getLoggedInUser() == null) return
        val taskId = intent.getIntExtra("taskId", -1)
        val operation = intent.getStringExtra("operation")
        if (operation == "expand") {
            val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val validIds = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, TodayTasksWidget::class.java))
            if (widgetId !in validIds || db.taskDao().task(taskId)?.active != true || db.taskDao().children(taskId).none { it.active }) return
            val prefs = context.getSharedPreferences("swallet_widget", Context.MODE_PRIVATE)
            val key = "expanded_$widgetId"
            val expanded = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
            if (!expanded.add(taskId.toString())) expanded.remove(taskId.toString())
            prefs.edit().putStringSet(key, expanded).apply()
        } else if (operation == null || operation == "complete") {
            db.withTransaction {
                if (com.example.financetracker.security.AppLock.isLocked(context)) return@withTransaction
                val task = db.taskDao().task(taskId)
                val parentActive = task?.parentTaskId?.let { db.taskDao().task(it)?.active } != false
                if (task != null && parentActive && PlannerRules.occurs(task, today)) {
                    if (intent.getBooleanExtra("complete", true)) db.taskDao().complete(TaskCompletion(
                        taskId = task.id, occurrenceDate = PlannerRules.completionDate(task, today), title = task.title, notes = task.notes))
                    else db.taskDao().undo(task.id, PlannerRules.completionDate(task, today))
                }
            }
            NotificationScheduler.rescheduleTasks(context)
        }
    }
    companion object {
        private const val REFRESH = "com.example.financetracker.widget.REFRESH"
        private const val TOGGLE = "com.example.financetracker.widget.TOGGLE"
        private val interactions = Mutex()
        fun refresh(context: Context) { context.sendBroadcast(Intent(context, TodayTasksWidget::class.java).setAction(REFRESH)) }
        private fun launch(context: Context, kind: String, code: Int) = PendingIntent.getActivity(context, code,
            Intent(context, QuickEntryActivity::class.java).setAction("com.example.financetracker.quick.$kind")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        suspend fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayTasksWidget::class.java))
            if (ids.isEmpty()) return
            ids.forEach { id ->
                val snapshot = TodayTasksSnapshot.load(context, id)
                val today = snapshot.date
                val tasks = snapshot.tasks
                val done = snapshot.done
                val signedIn = snapshot.signedIn
                val dark = TodayTasksSnapshot.isDark(context)
                val ink = android.graphics.Color.parseColor(if (dark) "#EDF3FC" else "#182B48")
                val muted = android.graphics.Color.parseColor(if (dark) "#A6B6CA" else "#677990")
                val views = RemoteViews(context.packageName, R.layout.widget_today_tasks)
                views.setInt(R.id.widgetRoot, "setBackgroundResource", if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light)
                views.setTextColor(R.id.widgetTitle, ink); views.setTextColor(R.id.widgetSubtitle, muted)
                views.setTextViewText(R.id.widgetSubtitle, "${today.format(DateTimeFormatter.ofPattern("EEE, d MMM"))} · ${snapshot.dueIds.count { it in done }}/${snapshot.dueIds.size} done")
                views.setOnClickPendingIntent(R.id.widgetHeader, launch(context, "today", 900))
                views.setOnClickPendingIntent(R.id.widgetAdd, launch(context, "task", 901))
                val gesture = when {
                    snapshot.dueIds.isEmpty() -> WalletGuideView.Gesture.WELCOME
                    snapshot.dueIds.all { it in done } -> WalletGuideView.Gesture.ENCOURAGE
                    else -> WalletGuideView.Gesture.PLAN
                }
                views.removeAllViews(R.id.widgetCharacter)
                views.addView(R.id.widgetCharacter, WidgetCharacter.views(context, gesture))
                views.setOnClickPendingIntent(R.id.widgetCharacter, launch(context, "today", 900))
                // Collection rows remain individually accessible, without truncating today's list.
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    val items = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1)
                    tasks.forEach { task -> items.addItem(task.id.toLong(), snapshot.row(context, task)) }
                    views.setRemoteAdapter(R.id.widgetRows, items.build())
                } else {
                    val service = Intent(context, TodayTasksService::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .setData(android.net.Uri.parse("swallet://today-tasks/$id"))
                    @Suppress("DEPRECATION")
                    views.setRemoteAdapter(R.id.widgetRows, service)
                }
                views.setEmptyView(R.id.widgetRows, R.id.widgetEmpty)
                views.setTextColor(R.id.widgetEmpty, muted)
                views.setTextViewText(R.id.widgetEmpty, if (signedIn) "No tasks today" else "Open SWallet to sign in")
                views.setPendingIntentTemplate(R.id.widgetRows, PendingIntent.getBroadcast(context, id,
                    Intent(context, TodayTasksWidget::class.java).setAction(TOGGLE),
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)))
                views.setTextColor(R.id.widgetFooter, muted)
                views.setTextViewText(R.id.widgetFooter, when {
                    !signedIn -> "Open SWallet to sign in"
                    com.example.financetracker.security.AppLock.isLocked(context) -> "Task details visible · Open SWallet to unlock changes"
                    tasks.isEmpty() -> "No tasks today · Tap + to add"
                    tasks.isNotEmpty() -> "Tap a task for subtasks · Check to complete"
                    else -> "Open checklist"
                })
                views.setOnClickPendingIntent(R.id.widgetFooter, launch(context, "today", 900))
                manager.updateAppWidget(id, views)
                if (android.os.Build.VERSION.SDK_INT < 31) {
                    @Suppress("DEPRECATION")
                    manager.notifyAppWidgetViewDataChanged(id, R.id.widgetRows)
                }
            }
        }
    }
}
