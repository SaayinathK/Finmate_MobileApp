package com.example.financetracker.activities

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import android.content.Intent
import com.example.financetracker.models.TaskCompletion
import com.example.financetracker.utils.PlannerRules
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.DailyTask
import com.example.financetracker.receiver.NotificationScheduler
import com.example.financetracker.ui.WalletScreen
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

open class TaskEditorActivity : WalletScreen() {
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var task: DailyTask? = null
    private lateinit var titleInput: TextInputEditText
    private lateinit var notesInput: TextInputEditText
    private var date = LocalDate.now()
    private var time = LocalTime.of(9,0)
    private var recurrence = 0
    private val days = mutableSetOf<Int>()
    private lateinit var remind: CheckBox
    private lateinit var important: CheckBox
    private var submitting = false
    private var parentId: Int? = null
    private var reminderDate = LocalDate.now()
    private var deadline = LocalTime.of(23, 59)
    private var untilDone = false
    private var childPanel: LinearLayout? = null
    private var childLoadJob: kotlinx.coroutines.Job? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val taskId = savedInstanceState?.getInt("savedTaskId", -1)?.takeIf { it > 0 } ?: intent.getIntExtra("taskId", -1)
            task = db.taskDao().task(taskId)
            if (taskId != -1 && task == null) { toast("This task is no longer available"); finish(); return@launch }
            parentId = task?.parentTaskId ?: intent.getIntExtra("parentTaskId", -1).takeIf { it > 0 }
            val parent = parentId?.let { db.taskDao().task(it) }
            if (parentId != null && (parent == null || parent.parentTaskId != null)) { toast("The parent task is no longer available"); finish(); return@launch }
            date = LocalDate.parse(savedInstanceState?.getString("date") ?: task?.startDate ?: LocalDate.now().toString())
            time = LocalTime.parse(savedInstanceState?.getString("time") ?: task?.reminderTime ?: "09:00")
            reminderDate = LocalDate.parse(savedInstanceState?.getString("reminderDate") ?: task?.reminderDate ?: date.toString())
            deadline = LocalTime.parse(savedInstanceState?.getString("deadline") ?: task?.deadlineTime ?: "23:59")
            untilDone = savedInstanceState?.getBoolean("untilDone") ?: task?.remindUntilDone ?: false
            recurrence = savedInstanceState?.getInt("recurrence") ?: when(task?.recurrence) { "DAILY" -> 1; "WEEKLY" -> 2; else -> 0 }
            days.addAll((savedInstanceState?.getString("days") ?: task?.weekdays ?: "").split(",").mapNotNull { it.toIntOrNull() })
            screen(if (task == null) "New task" else "Edit task", "", false)
            if (parent != null) card(color = softBlue).apply {
                addView(text("Subtask of ${parent.title}", 16, blue, true))
                addView(text("Set this subtask's own deadline and reminder.", 13, muted))
            }

            val form = card()
            titleInput = field(form, "Task name", savedInstanceState?.getString("title") ?: task?.title ?: "").apply { id = com.example.financetracker.R.id.taskName }
            notesInput = field(form, "Notes (optional)", savedInstanceState?.getString("notes") ?: task?.notes ?: "", multiline = true)
            important = CheckBox(this@TaskEditorActivity).apply {
                id = com.example.financetracker.R.id.taskImportant
                text = "Important"; setTextColor(ink); minHeight = dp(48)
                isChecked = savedInstanceState?.getBoolean("important") ?: task?.important ?: false
            }
            form.addView(important)
            form.addView(text("Show on Home when due", 12, muted))
            val schedule = card().apply { id = com.example.financetracker.R.id.taskSchedulePanel }
            section("Deadline & reminder", parent = schedule)
            fun dateLabel() = "${if (recurrence == 0) "Deadline" else "Starts"}: ${com.example.financetracker.utils.PlannerRules.displayDate(date)}"
            val dateButton = button(dateLabel(), schedule, true) { }.apply { id = com.example.financetracker.R.id.taskScheduleDate }
            val deadlineButton = button("Deadline time: $deadline", schedule, true) {}
            deadlineButton.setOnClickListener {
                TimePickerDialog(this@TaskEditorActivity, { _, h, m -> deadline = LocalTime.of(h,m); deadlineButton.text = "Deadline time: $deadline" }, deadline.hour, deadline.minute, android.text.format.DateFormat.is24HourFormat(this@TaskEditorActivity)).show()
            }
            val daysGroup = com.google.android.material.chip.ChipGroup(this@TaskEditorActivity)
            val oneOffReminder = column()
            choices(schedule, listOf("On date", "Daily", "Selected days"), recurrence) {
                recurrence = it; daysGroup.visibility = if (it == 2) android.view.View.VISIBLE else android.view.View.GONE
                oneOffReminder.visibility = if (it == 0) android.view.View.VISIBLE else android.view.View.GONE
                dateButton.text = dateLabel()
            }
            DayOfWeek.values().forEach { day ->
                daysGroup.addView(com.google.android.material.chip.Chip(this@TaskEditorActivity).apply {
                    text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()); isCheckable = true; isChecked = day.value in days
                    contentDescription = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
                    minHeight = dp(48); setEnsureMinTouchTargetSize(true)
                    setOnCheckedChangeListener { _, selected -> if (selected) days.add(day.value) else days.remove(day.value) }
                })
            }
            schedule.addView(daysGroup); daysGroup.visibility = if (recurrence == 2) android.view.View.VISIBLE else android.view.View.GONE
            remind = CheckBox(this@TaskEditorActivity).apply {
                id = com.example.financetracker.R.id.taskReminderEnabled
                text = "Remind me"; setTextColor(ink)
                isChecked = savedInstanceState?.getBoolean("remind") ?: task?.reminderEnabled ?: true; minHeight = dp(48)
            }
            schedule.addView(remind)
            val reminderDayButton = button("Reminder day: ${PlannerRules.displayDate(reminderDate)}", oneOffReminder, true) {}
            dateButton.setOnClickListener { pickDate(date) {
                if (reminderDate == date) { reminderDate = it; reminderDayButton.text = "Reminder day: ${PlannerRules.displayDate(it)}" }
                date = it; dateButton.text = dateLabel()
            } }
            reminderDayButton.setOnClickListener { pickDate(reminderDate) { reminderDate = it; reminderDayButton.text = "Reminder day: ${PlannerRules.displayDate(it)}" } }
            choices(oneOffReminder, listOf("On selected day", "On and after selected day"), if (untilDone) 1 else 0) { untilDone = it == 1 }
            oneOffReminder.addView(text("On and after: repeat daily at the selected time until completed, including after the deadline.", 13, muted))
            schedule.addView(oneOffReminder); oneOffReminder.visibility = if (recurrence == 0) android.view.View.VISIBLE else android.view.View.GONE
            val timeButton = button("Time: $time", schedule, true) { }.apply { id = com.example.financetracker.R.id.taskReminderTime }
            fun updateReminder() {
                timeButton.isEnabled = remind.isChecked; timeButton.alpha = if (remind.isChecked) 1f else 0.5f
                reminderDayButton.isEnabled = remind.isChecked; oneOffReminder.alpha = if (remind.isChecked) 1f else 0.5f
            }
            remind.setOnCheckedChangeListener { _, _ -> updateReminder() }; updateReminder()
            timeButton.setOnClickListener {
                TimePickerDialog(this@TaskEditorActivity, { _, h, m -> time = LocalTime.of(h,m); timeButton.text = "Time: $time" }, time.hour, time.minute, android.text.format.DateFormat.is24HourFormat(this@TaskEditorActivity)).show()
            }
            if (intent.getBooleanExtra("focusReminder", false) && savedInstanceState == null) {
                schedule.post { findViewById<android.widget.ScrollView>(com.example.financetracker.R.id.screenScroll).smoothScrollTo(0, schedule.top) }
            }
            if (parentId == null) {
                val children = card().apply { id = com.example.financetracker.R.id.taskSubtasksPanel }
                section("Subtasks", "Check to complete · Tap a row for details", children)
                childPanel = column().apply { tag = "subtaskList" }; children.addView(childPanel); loadChildren()
                button(if (task == null) "Save & add subtask" else "Add subtask", children, true) { save(true) }
            }
            button("Save") { save() }
            if (task != null) button(if (task!!.active) "Archive" else "Restore", outlined = true) {
                val active = !task!!.active
                AlertDialog.Builder(this@TaskEditorActivity).setTitle(if (active) "Restore this task?" else "Archive this task?")
                    .setMessage(if (active) "This task and its subtasks will appear on their scheduled days again." else "This task and its subtasks will be archived. Future reminders will stop. Completed history will stay.")
                    .setPositiveButton(if (active) "Restore" else "Archive") { _, _ -> lifecycleScope.launch {
                        db.withTransaction {
                            db.taskDao().update(task!!.copy(active = active))
                            db.taskDao().children(task!!.id).forEach { db.taskDao().update(it.copy(active = active)) }
                        }
                        NotificationScheduler.rescheduleTasks(this@TaskEditorActivity); finish()
                    } }.setNegativeButton("Cancel", null).show()
            }
            if (task != null) button("Delete task", outlined = true) {
                confirmDeletion("Delete this task?", "This deletes the task, its subtasks, completed history and future reminders.") {
                    lifecycleScope.launch {
                        try {
                            com.example.financetracker.repository.DataMaintenance.deleteTask(db, task!!.id)
                            NotificationScheduler.rescheduleTasks(this@TaskEditorActivity); toast("Task deleted"); finish()
                        } catch (_: Exception) { toast("Could not delete task") }
                    }
                }
            }
            animateEntrance(form)
        }
    }
    override fun onResume() { super.onResume(); loadChildren() }
    private fun loadChildren() {
        val panel = childPanel ?: return
        val saved = task ?: return
        childLoadJob?.cancel()
        childLoadJob = lifecycleScope.launch {
            val children = db.taskDao().children(saved.id)
            val completed = db.taskDao().completions()
            panel.removeAllViews()
            if (children.isEmpty()) panel.addView(text("No subtasks yet", 13, muted))
            children.forEachIndexed { index, child ->
                if (index > 0) panel.addView(android.view.View(this@TaskEditorActivity).apply { setBackgroundColor(border) },
                    LinearLayout.LayoutParams(-1, dp(1)).apply { marginStart = dp(48) })
                val isDone = PlannerRules.isCompleted(child, LocalDate.now(), completed)
                val item = row().apply {
                    tag = "subtask:${child.id}"; minimumHeight = dp(72); setPadding(0, dp(8), 0, dp(8)); isFocusable = true
                    contentDescription = "Edit subtask ${child.title}"
                    setOnClickListener { startActivity(Intent(this@TaskEditorActivity, TaskEditorActivity::class.java).putExtra("taskId", child.id)) }
                    val ripple = android.util.TypedValue(); theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
                    foreground = androidx.core.content.ContextCompat.getDrawable(this@TaskEditorActivity, ripple.resourceId)
                }
                item.addView(CheckBox(this@TaskEditorActivity).apply {
                    tag = "subtaskCheck:${child.id}"; minHeight = dp(48)
                    contentDescription = "${if (isDone) "Mark incomplete" else "Complete"} ${child.title}"
                    isChecked = isDone
                    isEnabled = child.recurrence == "ONCE" || PlannerRules.occurs(child, LocalDate.now())
                    setOnCheckedChangeListener { _, checked ->
                        isEnabled = false
                        lifecycleScope.launch {
                            try {
                                val key = PlannerRules.completionDate(child, LocalDate.now())
                                if (checked) db.taskDao().complete(TaskCompletion(taskId = child.id, occurrenceDate = key, title = child.title, notes = child.notes))
                                else db.taskDao().undo(child.id, key)
                                NotificationScheduler.rescheduleTasks(this@TaskEditorActivity)
                            } catch (_: Exception) { toast("Could not update subtask") }
                            loadChildren()
                        }
                    }
                }, LinearLayout.LayoutParams(dp(48), dp(48)))
                item.addView(column().apply {
                    addView(text(child.title, 15, if (isDone) green else ink, true).apply {
                        maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                        if (isDone) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                    })
                    addView(text(PlannerRules.taskReminder(child) + if (child.active) "" else " · Archived", 12, muted).apply {
                        maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                item.addView(android.widget.ImageView(this@TaskEditorActivity).apply {
                    setImageResource(com.example.financetracker.R.drawable.ic_chevron_right); setColorFilter(muted); contentDescription = null
                }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginStart = dp(8) })
                panel.addView(item, LinearLayout.LayoutParams(-1, -2))
                com.example.financetracker.ui.WalletMotion.press(item)
            }
            if (intent.getBooleanExtra("focusSubtasks", false)) panel.post {
                findViewById<android.widget.ScrollView>(com.example.financetracker.R.id.screenScroll).smoothScrollTo(0,
                    findViewById<android.view.View>(com.example.financetracker.R.id.taskSubtasksPanel).top)
            }
        }
    }
    private fun save(addChild: Boolean = false) {
        if (submitting) return
        val title = titleInput.text.toString().trim()
        if (title.isBlank()) { titleInput.error = "Enter a task name"; return }
        if (recurrence == 2 && days.isEmpty()) { toast("Choose at least one weekday"); return }
        val scheduleChanged = task == null || task?.recurrence != "ONCE" || (task?.reminderDate ?: task?.startDate) != reminderDate.toString() || task?.reminderTime != time.toString() || task?.reminderEnabled != remind.isChecked || task?.remindUntilDone != untilDone
        if (recurrence == 0 && !untilDone && remind.isChecked && scheduleChanged && reminderDate.atTime(time) <= java.time.LocalDateTime.now()) { toast("Choose a future reminder, enable on and after, or turn the reminder off"); return }
        submitting = true
        lifecycleScope.launch {
            try {
                var updated = DailyTask(id = task?.id ?: 0, title = title, notes = notesInput.text.toString().trim(), startDate = date.toString(),
                    recurrence = listOf("ONCE", "DAILY", "WEEKLY")[recurrence], weekdays = days.sorted().joinToString(","), reminderTime = time.toString(), reminderEnabled = remind.isChecked,
                    active = task?.active ?: true, createdAt = task?.createdAt ?: System.currentTimeMillis(), important = important.isChecked,
                    parentTaskId = parentId, deadlineTime = deadline.toString(), reminderDate = if (recurrence == 0) reminderDate.toString() else null, remindUntilDone = recurrence == 0 && untilDone)
                db.withTransaction {
                    if (parentId != null && db.taskDao().task(parentId!!) == null) error("Parent removed")
                    if (task == null) updated = updated.copy(id = db.taskDao().insert(updated).toInt())
                    else {
                        if (task!!.startDate != updated.startDate && task!!.recurrence == "ONCE" && updated.recurrence == "ONCE") {
                            db.taskDao().completions().firstOrNull { it.taskId == updated.id && it.occurrenceDate == task!!.startDate }?.let {
                                db.taskDao().undo(updated.id, it.occurrenceDate); db.taskDao().complete(it.copy(id = 0, occurrenceDate = updated.startDate))
                            }
                        }
                        db.taskDao().update(updated)
                    }
                }
                task = updated; NotificationScheduler.rescheduleTasks(this@TaskEditorActivity); submitting = false
                if (addChild) startActivity(Intent(this@TaskEditorActivity, TaskEditorActivity::class.java).putExtra("parentTaskId", updated.id))
                else { toast("Task saved"); finish() }
            } catch (_: Exception) { submitting = false; toast("Could not save task. Please try again.") }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (::titleInput.isInitialized) {
            outState.putString("title", titleInput.text.toString()); outState.putString("notes", notesInput.text.toString()); outState.putString("date", date.toString())
            outState.putString("time", time.toString()); outState.putInt("recurrence", recurrence); outState.putString("days", days.joinToString(",")); outState.putBoolean("remind", remind.isChecked)
            outState.putBoolean("important", important.isChecked)
            outState.putString("reminderDate", reminderDate.toString()); outState.putString("deadline", deadline.toString()); outState.putBoolean("untilDone", untilDone)
            task?.let { outState.putInt("savedTaskId", it.id) }
        }; super.onSaveInstanceState(outState)
    }
}
