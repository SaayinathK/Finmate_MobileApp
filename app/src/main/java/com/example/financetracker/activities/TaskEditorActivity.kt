package com.example.financetracker.activities

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
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
    private var submitting = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            task = db.taskDao().task(intent.getIntExtra("taskId", -1))
            date = LocalDate.parse(savedInstanceState?.getString("date") ?: task?.startDate ?: LocalDate.now().toString())
            time = LocalTime.parse(savedInstanceState?.getString("time") ?: task?.reminderTime ?: "09:00")
            recurrence = savedInstanceState?.getInt("recurrence") ?: when(task?.recurrence) { "DAILY" -> 1; "WEEKLY" -> 2; else -> 0 }
            days.addAll((savedInstanceState?.getString("days") ?: task?.weekdays ?: "").split(",").mapNotNull { it.toIntOrNull() })
            screen(if (task == null) "New task" else "Edit task", "", false)

            val form = card()
            titleInput = field(form, "Task name", savedInstanceState?.getString("title") ?: task?.title ?: "")
            notesInput = field(form, "Notes (optional)", savedInstanceState?.getString("notes") ?: task?.notes ?: "", multiline = true)
            val dateButton = button("Starts ${date}", form, true) { pickDate(date) { date = it; updateDateButton(form) } }
            dateButton.tag = "date"
            form.addView(text("Repeat", 14, muted, true))
            val daysGroup = com.google.android.material.chip.ChipGroup(this@TaskEditorActivity)
            choices(form, listOf("Once", "Daily", "Selected days"), recurrence) { recurrence = it; daysGroup.visibility = if (it == 2) android.view.View.VISIBLE else android.view.View.GONE }
            DayOfWeek.values().forEach { day ->
                daysGroup.addView(com.google.android.material.chip.Chip(this@TaskEditorActivity).apply {
                    text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()); isCheckable = true; isChecked = day.value in days
                    minHeight = dp(48); setOnCheckedChangeListener { _, selected -> if (selected) days.add(day.value) else days.remove(day.value) }
                })
            }
            form.addView(daysGroup); daysGroup.visibility = if (recurrence == 2) android.view.View.VISIBLE else android.view.View.GONE
            remind = CheckBox(this@TaskEditorActivity).apply { text = "Task reminder"; isChecked = savedInstanceState?.getBoolean("remind") ?: task?.reminderEnabled ?: true; minHeight = dp(48) }
            form.addView(remind)
            val timeButton = button("Time: $time", form, true) { }
            timeButton.setOnClickListener {
                TimePickerDialog(this@TaskEditorActivity, { _, h, m -> time = LocalTime.of(h,m); timeButton.text = "Time: $time" }, time.hour, time.minute, false).show()
            }
            form.addView(text("Completion is tracked per date.", 13, muted))
            button("Save") { save() }
            if (task != null) button("Archive", outlined = true) {
                AlertDialog.Builder(this@TaskEditorActivity).setTitle("Archive this task?").setMessage("Future reminders will stop. Completed history will stay available.")
                    .setPositiveButton("Archive") { _, _ -> lifecycleScope.launch { db.taskDao().update(task!!.copy(active = false)); NotificationScheduler.rescheduleTasks(this@TaskEditorActivity); finish() } }.setNegativeButton("Cancel", null).show()
            }
            if (task != null) button("Delete task", outlined = true) {
                confirmDeletion("Delete this task?", "This deletes the task, its completed history and future reminders.") {
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
    private fun updateDateButton(form: LinearLayout) { form.findViewWithTag<android.widget.Button>("date").text = "Starts $date" }
    private fun save() {
        if (submitting) return
        val title = titleInput.text.toString().trim()
        if (title.isBlank()) { titleInput.error = "Enter a task name"; return }
        if (recurrence == 2 && days.isEmpty()) { toast("Choose at least one weekday"); return }
        if (recurrence == 0 && remind.isChecked && date.atTime(time) <= java.time.LocalDateTime.now()) { toast("Choose a future reminder time, or turn the reminder off for a past task"); return }
        submitting = true
        lifecycleScope.launch {
            try {
                val updated = DailyTask(id = task?.id ?: 0, title = title, notes = notesInput.text.toString().trim(), startDate = date.toString(),
                    recurrence = listOf("ONCE", "DAILY", "WEEKLY")[recurrence], weekdays = days.sorted().joinToString(","), reminderTime = time.toString(), reminderEnabled = remind.isChecked,
                    active = task?.active ?: true, createdAt = task?.createdAt ?: System.currentTimeMillis())
                if (task == null) db.taskDao().insert(updated) else db.taskDao().update(updated)
                NotificationScheduler.rescheduleTasks(this@TaskEditorActivity); toast("Task saved"); finish()
            } catch (_: Exception) { submitting = false; toast("Could not save task. Please try again.") }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (::titleInput.isInitialized) {
            outState.putString("title", titleInput.text.toString()); outState.putString("notes", notesInput.text.toString()); outState.putString("date", date.toString())
            outState.putString("time", time.toString()); outState.putInt("recurrence", recurrence); outState.putString("days", days.joinToString(",")); outState.putBoolean("remind", remind.isChecked)
        }; super.onSaveInstanceState(outState)
    }
}
