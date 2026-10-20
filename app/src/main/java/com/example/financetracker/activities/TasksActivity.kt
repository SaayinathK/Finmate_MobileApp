package com.example.financetracker.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.example.financetracker.R
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.DailyTask
import com.example.financetracker.models.TaskCompletion
import com.example.financetracker.receiver.NotificationScheduler
import com.example.financetracker.ui.WalletScreen
import com.example.financetracker.ui.WalletMotion
import com.example.financetracker.ui.WalletGuideView
import com.example.financetracker.utils.PlannerRules
import com.example.financetracker.utils.ReportPeriod
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TasksActivity : WalletScreen() {
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var period = ReportPeriod.DAY
    private var anchor = LocalDate.now()
    private var history = false
    private var showAll = false
    private var renderJob: Job? = null
    private var taskNames = emptyMap<Int, String>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        period = ReportPeriod.values()[savedInstanceState?.getInt("period") ?: 0]
        anchor = savedInstanceState?.getString("anchor")?.let(LocalDate::parse) ?: LocalDate.now()
        history = savedInstanceState?.getBoolean("history") ?: false
        showAll = savedInstanceState?.getBoolean("showAll") ?: false
        lifecycleScope.launch { combine(db.taskDao().observeTasks(), db.taskDao().observeCompletions()) { a, b -> a to b }.collect { render() } }
    }
    override fun onResume() { super.onResume(); render() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("period", period.ordinal); outState.putString("anchor", anchor.toString())
        outState.putBoolean("history", history); outState.putBoolean("showAll", showAll)
        super.onSaveInstanceState(outState)
    }
    private fun render() {
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            val tasks = db.taskDao().allTasks()
            taskNames = tasks.associate { it.id to it.title }
            val completions = db.taskDao().completions()
            screen("Tasks", floatingAction = "Add task" to { open(TaskEditorActivity::class.java) })
            choices(content, listOf("Checklist", "History", "All tasks"), if (showAll) 2 else if (history) 1 else 0) {
                history = it == 1; showAll = it == 2; render()
            }
            if (showAll) {
                section("All tasks", "${tasks.size} tasks")
                if (tasks.isEmpty()) emptyState(R.drawable.art_checklist, "No tasks", "Add a task to get started")
                else {
                    val list = listPanel()
                    tasks.sortedWith(compareByDescending<DailyTask> { it.active }.thenByDescending { it.important }.thenBy { it.reminderTime }).forEach { task ->
                        taskRow(list, task, PlannerRules.taskReminder(task) + if (task.active) "" else " \u00b7 Archived")
                    }
                }
                return@launch
            }
            periodPicker(period, anchor) { p, d -> period = p; anchor = d; render() }
            val (start, end) = PlannerRules.bounds(anchor, period)
            val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.toList()
            val done = completions.filter { Instant.ofEpochMilli(it.completedAt).atZone(ZoneId.systemDefault()).toLocalDate() in start..end }
            val occurrences = dates.flatMap { date -> tasks.filter { PlannerRules.occurs(it, date, completions) }
                .sortedByDescending { it.important }.map { it to date } }
                .distinctBy { it.first.id to PlannerRules.completionDate(it.first, it.second) }
            val plannedKeys = (occurrences.map { it.first.id to PlannerRules.completionDate(it.first, it.second) } + done.map { it.taskId to it.occurrenceDate }).toSet()
            val dueSoFar = plannedKeys.count { LocalDate.parse(it.second) <= LocalDate.now() }
            val completedKeys = completions.map { it.taskId to it.occurrenceDate }.toSet()
            val doneCount = plannedKeys.count { it in completedKeys }
            card(color = android.graphics.Color.rgb(231, 243, 255)).apply {
                val line = row()
                line.addView(column().apply {
                    addView(text("$doneCount done / ${plannedKeys.size} planned", 16, blue, true))
                    addView(text(if (dueSoFar == 0) "No tasks due" else "${(doneCount * 100 / dueSoFar).coerceAtMost(100)}% complete", 13, muted))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                line.addView(guide(if (dueSoFar > 0 && doneCount >= dueSoFar) WalletGuideView.Gesture.ENCOURAGE else WalletGuideView.Gesture.PLAN, 100))
                addView(line)
            }
            if (history) {
                section("Completed")
                if (done.isEmpty()) emptyState(R.drawable.art_checklist, "No completed tasks")
                else {
                    val list = listPanel()
                    done.forEach { completion ->
                        val current = tasks.firstOrNull { it.id == completion.taskId }
                        val display = current?.copy(title = completion.title) ?: DailyTask(id = completion.taskId, title = completion.title, startDate = completion.occurrenceDate)
                        taskRow(list, display, "Completed \u00b7 ${PlannerRules.displayDate(LocalDate.parse(completion.occurrenceDate))}", completed = true) {
                            completionDetails(completion, current)
                        }
                    }
                }
            } else {
                section("Checklist")
                if (occurrences.isEmpty()) emptyState(R.drawable.art_checklist, "No tasks", "Add a task to get started")
                else {
                    val list = listPanel()
                    occurrences.forEach { (task, date) ->
                        val completed = PlannerRules.isCompleted(task, date, completions)
                        val detail = if (task.recurrence == "ONCE") PlannerRules.taskReminder(task) else "${PlannerRules.displayDate(date)} \u00b7 ${PlannerRules.taskReminder(task)}"
                        taskRow(list, task, detail, completed, date)
                    }
                }
            }
        }
    }
    private fun listPanel() = card().apply { setPadding(0, 0, 0, 0) }
    private fun taskRow(parent: LinearLayout, task: DailyTask, detail: String, completed: Boolean = false,
                        date: LocalDate? = null, onOpen: () -> Unit = { openTask(task) }) {
        if (parent.childCount > 0) parent.addView(View(this).apply { setBackgroundColor(border) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { marginStart = dp(56); marginEnd = dp(12) })
        val item = row().apply {
            tag = "task:${task.id}"; minimumHeight = dp(72); setPadding(dp(8), dp(8), dp(12), dp(8))
            isFocusable = true; contentDescription = "View ${task.title}${if (task.important) ", important" else ""}"
            setOnClickListener { onOpen() }
            val ripple = android.util.TypedValue(); theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            foreground = androidx.core.content.ContextCompat.getDrawable(this@TasksActivity, ripple.resourceId)
        }
        if (date != null) item.addView(CheckBox(this).apply {
            isChecked = completed; isEnabled = date <= LocalDate.now(); minHeight = dp(48)
            contentDescription = "${if (completed) "Mark incomplete" else "Complete"} ${task.title}"
            setOnCheckedChangeListener { _, checked -> isEnabled = false; setDone(task, date, checked) }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        else item.addView(badge(R.drawable.ic_tasks, if (completed) green else blue),
            LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginStart = dp(8); marginEnd = dp(8) })
        item.addView(column().apply {
            addView(text((if (task.parentTaskId != null) "\u21b3 " else "") + (if (task.important) "\u2605  " else "") + task.title, 15, if (completed) green else ink, true).apply {
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                if (completed) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            })
            task.parentTaskId?.let { addView(text("Part of ${taskNames[it] ?: "task"}", 12, blue)) }
            addView(text(detail, 12, muted).apply { maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        item.addView(ImageView(this).apply { setImageResource(R.drawable.ic_chevron_right); setColorFilter(muted); contentDescription = null },
            LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginStart = dp(8) })
        parent.addView(item, LinearLayout.LayoutParams(-1, -2)); WalletMotion.press(item)
    }
    private fun openTask(task: DailyTask) { startActivity(Intent(this, TaskEditorActivity::class.java).putExtra("taskId", task.id)) }
    private fun setDone(task: DailyTask, date: LocalDate, checked: Boolean) {
        lifecycleScope.launch {
            try {
                db.withTransaction {
                    val latest = db.taskDao().task(task.id)
                    if (checked && latest != null && PlannerRules.occurs(latest, date)) db.taskDao().complete(
                        TaskCompletion(taskId = task.id, occurrenceDate = PlannerRules.completionDate(latest, date), title = latest.title, notes = latest.notes))
                    else if (!checked) db.taskDao().undo(task.id, PlannerRules.completionDate(task, date))
                }
                NotificationScheduler.rescheduleTasks(this@TasksActivity)
            } catch (_: Exception) { toast("Could not update task"); render() }
        }
    }
    private fun completionDetails(completion: TaskCompletion, task: DailyTask?) {
        val finished = Instant.ofEpochMilli(completion.completedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
        val dialog = MaterialAlertDialogBuilder(this).setTitle(completion.title)
            .setMessage("Scheduled: ${PlannerRules.displayDate(LocalDate.parse(completion.occurrenceDate))}\nFinished: $finished" + if (completion.notes.isBlank()) "" else "\n\n${completion.notes}")
            .setNegativeButton("Close", null)
            .setNeutralButton("Undo") { _, _ -> lifecycleScope.launch {
                db.taskDao().undo(completion.taskId, completion.occurrenceDate); NotificationScheduler.rescheduleTasks(this@TasksActivity)
            } }
        if (task != null) dialog.setPositiveButton("Task details") { _, _ -> openTask(task) }
        dialog.show()
    }
}
