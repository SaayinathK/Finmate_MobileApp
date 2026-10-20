package com.example.financetracker.activities

import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.TaskCompletion
import com.example.financetracker.receiver.NotificationScheduler
import com.example.financetracker.ui.WalletScreen
import com.example.financetracker.ui.WalletMotion
import com.example.financetracker.R
import com.example.financetracker.utils.PlannerRules
import com.example.financetracker.utils.ReportPeriod
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TasksActivity : WalletScreen() {
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var period = ReportPeriod.DAY
    private var anchor = LocalDate.now()
    private var history = false
    private var renderJob: Job? = null
    private var recentCompletion: Pair<Int, String>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        period = ReportPeriod.values()[savedInstanceState?.getInt("period") ?: 0]
        anchor = savedInstanceState?.getString("anchor")?.let(LocalDate::parse) ?: LocalDate.now()
        history = savedInstanceState?.getBoolean("history") ?: false
        lifecycleScope.launch { combine(db.taskDao().observeTasks(), db.taskDao().observeCompletions()) { a,b -> a to b }.collect { render() } }
    }
    override fun onResume() { super.onResume(); render() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("period", period.ordinal); outState.putString("anchor", anchor.toString()); outState.putBoolean("history", history); super.onSaveInstanceState(outState) }
    private fun render() {
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            val tasks = db.taskDao().allTasks()
            val completions = db.taskDao().completions()
            screen("Tasks", floatingAction = "Add task" to { open(TaskEditorActivity::class.java) })
            choices(content, listOf("Checklist", "History"), if (history) 1 else 0) { history = it == 1; render() }
            periodPicker(period, anchor) { p, d -> period = p; anchor = d; render() }
            val (start, end) = PlannerRules.bounds(anchor, period)
            val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.toList()
            val done = completions.filter { LocalDate.parse(it.occurrenceDate) in start..end }
            val occurrences = dates.flatMap { date -> tasks.filter { PlannerRules.occurs(it, date) }.map { it to date } }
            val plannedKeys = (occurrences.map { it.first.id to it.second.toString() } + done.map { it.taskId to it.occurrenceDate }).toSet()
            val dueSoFar = plannedKeys.count { LocalDate.parse(it.second) <= LocalDate.now() }
            card(color = android.graphics.Color.rgb(231, 243, 255)).apply {
                addView(text("${done.size} done / ${plannedKeys.size} planned", 16, blue, true))
                addView(text(if (dueSoFar == 0) "No tasks due" else "${(done.size * 100 / dueSoFar).coerceAtMost(100)}% complete", 13, muted))
            }
            if (history) {
                section("Completed")
                if (done.isEmpty()) emptyState(R.drawable.art_checklist, "No completed tasks")
                done.forEach { completion ->
                    card().apply {
                        addView(text("✓  ${completion.title}", 17, green, true))
                        addView(text("Scheduled: ${PlannerRules.displayDate(LocalDate.parse(completion.occurrenceDate))}", 13, muted))
                        addView(text("Finished: ${Instant.ofEpochMilli(completion.completedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))}", 13, muted))
                        if (completion.notes.isNotBlank()) addView(text(completion.notes))
                        button("Undo", this, true) {
                            lifecycleScope.launch { db.taskDao().undo(completion.taskId, completion.occurrenceDate); NotificationScheduler.rescheduleTasks(this@TasksActivity) }
                        }
                    }
                }
            } else {
                section("Checklist")
                if (occurrences.isEmpty()) emptyState(R.drawable.art_checklist, "No tasks", "Add a task to get started")
                occurrences.forEach { (task, date) ->
                    val completed = completions.any { it.taskId == task.id && it.occurrenceDate == date.toString() }
                    val c = card(color = if (completed) android.graphics.Color.rgb(237,249,244) else surface)
                    val check = CheckBox(this@TasksActivity).apply {
                        text = task.title; textSize = 15f; setTextColor(if (completed) green else ink); minHeight = dp(48)
                        isChecked = completed; isEnabled = date <= LocalDate.now()
                        if (completed) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                        contentDescription = "${task.title}, ${PlannerRules.displayDate(date)}"
                        setOnCheckedChangeListener { _, checked ->
                            isEnabled = false
                            lifecycleScope.launch {
                                try {
                                    if (checked) {
                                        recentCompletion = task.id to date.toString()
                                        db.taskDao().complete(TaskCompletion(taskId = task.id, occurrenceDate = date.toString(), title = task.title, notes = task.notes))
                                    }
                                    else db.taskDao().undo(task.id, date.toString())
                                    NotificationScheduler.rescheduleTasks(this@TasksActivity)
                                } catch (_: Exception) { toast("Could not update task. Please try again."); render() }
                            }
                        }
                    }
                    c.addView(check)
                    c.addView(text("${PlannerRules.displayDate(date)} · ${task.reminderTime} · ${when(task.recurrence) { "DAILY" -> "Every day"; "WEEKLY" -> "Selected weekdays"; else -> "One time" }}", 12, muted))
                    if (task.notes.isNotBlank()) c.addView(text(task.notes, 14, muted))
                    c.isFocusable = true
                    c.contentDescription = "Edit ${task.title}"
                    c.setOnClickListener { startActivity(Intent(this@TasksActivity, TaskEditorActivity::class.java).putExtra("taskId", task.id)) }
                    WalletMotion.press(c)
                    if (completed && recentCompletion == (task.id to date.toString())) {
                        WalletMotion.confirm(c); recentCompletion = null
                    }
                }
                button("All tasks", outlined = true) {
                    val active = tasks.sortedByDescending { it.active }
                    if (active.isEmpty()) toast("Create a task to begin") else AlertDialog.Builder(this@TasksActivity).setTitle("Your tasks & routines")
                        .setItems(active.map { it.title + if (it.active) "" else " (archived)" }.toTypedArray()) { _, index -> startActivity(Intent(this@TasksActivity, TaskEditorActivity::class.java).putExtra("taskId", active[index].id)) }.show()
                }
            }
            button("Reminders", outlined = true) { open(ReminderSettingsActivity::class.java) }
        }
    }
}
