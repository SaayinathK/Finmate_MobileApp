package com.example.financetracker

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.financetracker.activities.*
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.DailyTask
import com.example.financetracker.repository.DataMaintenance
import com.example.financetracker.receiver.NotificationScheduler
import com.google.android.material.chip.Chip
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TaskControlsTest {
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrument.targetContext
    private val db get() = AppDatabase.getDatabase(context)
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun await(check: () -> Boolean) { repeat(80) { if (check()) return; Thread.sleep(100) }; assertTrue("Task UI timed out", check()) }
    private fun dialog(predicate: (View) -> Boolean): View {
        check(Build.VERSION.SDK_INT >= 29)
        return android.view.inspector.WindowInspector.getGlobalWindowViews().last { it.isShown && predicate(it) }
    }
    private fun shot(name: String) {
        instrument.waitForIdleSync(); Thread.sleep(600)
        val folder = java.io.File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        instrument.uiAutomation.takeScreenshot()?.let { bitmap -> java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
    }
    @Test fun taskListOpensDetailsAndKeepsActionsOffRows() {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val label = "QA controls ${System.nanoTime()}"
        val id = runBlocking { db.taskDao().insert(DailyTask(title = label, notes = "Full task details", startDate = LocalDate.now().toString(), recurrence = "DAILY", reminderEnabled = false)).toInt() }
        try {
            ActivityScenario.launch<Activity>(Intent(context, TasksActivity::class.java)).use { scenario ->
                await { var ready = false; scenario.onActivity { ready = it.findViewById<View>(android.R.id.content).findViewWithTag<View>("task:$id") != null }; ready }
                scenario.onActivity { activity ->
                    val row = activity.findViewById<View>(android.R.id.content).findViewWithTag<View>("task:$id")
                    assertTrue(views(row).filterIsInstance<Button>().none { it.text.toString() in listOf("Edit", "Reminder", "Delete", "Undo") })
                    assertFalse(views(row).filterIsInstance<TextView>().any { it.text.toString() == "Full task details" })
                }
                shot("TaskList")
                val monitor = instrument.addMonitor(TaskEditorActivity::class.java.name, null, false)
                var opened: Activity? = null
                try {
                    scenario.onActivity { it.findViewById<View>(android.R.id.content).findViewWithTag<View>("task:$id").performClick() }
                    opened = instrument.waitForMonitorWithTimeout(monitor, 5000)
                    assertNotNull(opened)
                    val editor = opened!!
                    await { var ready = false; instrument.runOnMainSync { ready = editor.findViewById<View>(R.id.taskImportant) != null }; ready }
                    instrument.runOnMainSync {
                        assertEquals(id, editor.intent.getIntExtra("taskId", -1))
                        assertTrue(views(editor.findViewById(android.R.id.content)).filterIsInstance<EditText>().any { it.text.toString() == "Full task details" })
                        editor.findViewById<CheckBox>(R.id.taskImportant).isChecked = true
                    }
                    editor.runOnUiThread { views(editor.findViewById(android.R.id.content)).filterIsInstance<Button>().first { it.text.toString() == "Save" }.performClick() }
                    await { runBlocking { db.taskDao().task(id)?.important == true } }
                } finally { instrument.runOnMainSync { opened?.finish() }; instrument.removeMonitor(monitor) }
                runBlocking { db.taskDao().update(db.taskDao().task(id)!!.copy(active = false)) }
                scenario.onActivity { activity -> views(activity.findViewById(android.R.id.content)).filterIsInstance<Chip>().first { it.text.toString() == "All tasks" }.performClick() }
                await { var ready = false; scenario.onActivity { ready = it.findViewById<View>(android.R.id.content).findViewWithTag<View>("task:$id") != null }; ready }
                val deleteMonitor = instrument.addMonitor(TaskEditorActivity::class.java.name, null, false)
                var details: Activity? = null
                try {
                    scenario.onActivity { it.findViewById<View>(android.R.id.content).findViewWithTag<View>("task:$id").performClick() }
                    details = instrument.waitForMonitorWithTimeout(deleteMonitor, 5000)
                    assertNotNull(details)
                    val editor = details!!
                    await { var ready = false; instrument.runOnMainSync { ready = editor.findViewById<View>(R.id.taskImportant) != null }; ready }
                    instrument.runOnMainSync {
                        assertTrue(editor.findViewById<CheckBox>(R.id.taskImportant).isChecked)
                        views(editor.findViewById(android.R.id.content)).filterIsInstance<Button>().first { it.text.toString() == "Delete task" }.performClick()
                    }
                    instrument.waitForIdleSync()
                    instrument.runOnMainSync { dialog { it.findViewById<View>(android.R.id.button1) != null }.findViewById<View>(android.R.id.button1).performClick() }
                    instrument.waitForIdleSync()
                    assertNotNull(runBlocking { db.taskDao().task(id) })
                    instrument.runOnMainSync {
                        val root = dialog { it.findViewById<View>(R.id.deleteVerification) != null }
                        root.findViewById<EditText>(R.id.deleteVerification).setText("DELETE")
                        root.findViewById<View>(android.R.id.button1).performClick()
                    }
                    await { runBlocking { db.taskDao().task(id) == null } }
                } finally { instrument.runOnMainSync { details?.finish() }; instrument.removeMonitor(deleteMonitor) }
            }
        } finally { runBlocking { DataMaintenance.deleteTask(db, id); NotificationScheduler.rescheduleTasks(context) } }
    }

    @Test fun eachReminderModePersistsItsOwnDateTimeAndWeekdays() {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val date = LocalDate.now().plusDays(3)
        val id = runBlocking { db.taskDao().insert(DailyTask(title = "QA reminder ${System.nanoTime()}", startDate = date.minusDays(1).toString(), reminderEnabled = false)).toInt() }
        try {
            for ((label, stored) in listOf("Daily" to "DAILY", "On date" to "ONCE", "Selected days" to "WEEKLY")) {
                ActivityScenario.launch<Activity>(Intent(context, TaskEditorActivity::class.java).putExtra("taskId", id)).use { scenario ->
                    await { var ready = false; scenario.onActivity { ready = it.findViewById<View>(R.id.taskReminderTime) != null }; ready }
                    scenario.onActivity { activity ->
                        views(activity.findViewById(android.R.id.content)).filterIsInstance<Chip>().first { it.text.toString() == label }.performClick()
                        if (stored == "DAILY") assertFalse(activity.findViewById<View>(R.id.taskReminderTime).isEnabled)
                        activity.findViewById<CheckBox>(R.id.taskReminderEnabled).isChecked = true
                        assertTrue(activity.findViewById<View>(R.id.taskReminderTime).isEnabled)
                        if (stored == "WEEKLY") {
                            listOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY).forEach { day ->
                                views(activity.findViewById(android.R.id.content)).filterIsInstance<Chip>().first { it.contentDescription?.toString() == day.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) }.isChecked = true
                            }
                        }
                        activity.findViewById<CheckBox>(R.id.taskImportant).isChecked = true
                        activity.findViewById<View>(R.id.taskReminderTime).performClick()
                    }
                    instrument.waitForIdleSync()
                    instrument.runOnMainSync {
                        val root = dialog { views(it).any { view -> view is TimePicker } }
                        views(root).filterIsInstance<TimePicker>().single().apply { hour = 17; minute = 35 }
                        root.findViewById<View>(android.R.id.button1).performClick()
                    }
                    scenario.onActivity { it.findViewById<View>(R.id.taskScheduleDate).performClick() }
                    instrument.waitForIdleSync()
                    instrument.runOnMainSync {
                        val root = dialog { views(it).any { view -> view is DatePicker } }
                        views(root).filterIsInstance<DatePicker>().single().updateDate(date.year, date.monthValue - 1, date.dayOfMonth)
                        root.findViewById<View>(android.R.id.button1).performClick()
                    }
                    scenario.recreate()
                    await { var ready = false; scenario.onActivity { ready = it.findViewById<Button>(R.id.taskReminderTime)?.text?.toString() == "Time: 17:35" }; ready }
                    scenario.onActivity { assertTrue(it.findViewById<CheckBox>(R.id.taskImportant).isChecked) }
                    if (stored == "WEEKLY") shot("TaskReminderSettings")
                    scenario.onActivity { activity -> views(activity.findViewById(android.R.id.content)).filterIsInstance<Button>().first { it.text.toString() == "Save" }.performClick() }
                    await { runBlocking { db.taskDao().task(id)?.let { it.recurrence == stored && it.reminderEnabled && it.reminderTime == "17:35" } == true } }
                    val task = runBlocking { db.taskDao().task(id)!! }
                    assertEquals(date.toString(), task.startDate)
                    if (stored == "WEEKLY") assertEquals("1,5", task.weekdays)
                }
            }
        } finally { runBlocking { DataMaintenance.deleteTask(db, id); NotificationScheduler.rescheduleTasks(context) } }
    }
    @Test fun dashboardShowsOnlyImportantTasksDueToday() {
        val today = LocalDate.now()
        val ids = runBlocking { (0..7).map { db.taskDao().insert(DailyTask(title = "QA home $it", startDate = today.toString(), reminderEnabled = false, important = it % 2 == 0)).toInt() } }
        val future = runBlocking { db.taskDao().insert(DailyTask(title = "QA future", startDate = today.plusDays(1).toString(), important = true)).toInt() }
        try {
            ActivityScenario.launch<Activity>(Intent(context, HomeActivity::class.java)).use { scenario ->
                await { var ready = false; scenario.onActivity { activity -> ready = ids.filterIndexed { index, _ -> index % 2 == 0 }.all { activity.findViewById<View>(android.R.id.content).findViewWithTag<View>("todayTask:$it") != null } }; ready }
                scenario.onActivity { activity ->
                    val root = activity.findViewById<View>(android.R.id.content)
                    assertNull(root.findViewWithTag<View>("todayTask:$future"))
                    ids.forEachIndexed { index, id ->
                        if (index % 2 == 0) assertNotNull(root.findViewWithTag<View>("todayTask:$id"))
                        else assertNull(root.findViewWithTag<View>("todayTask:$id"))
                    }
                }
                shot("HomeImportantTasks")
            }
        } finally { runBlocking { (ids + future).forEach { DataMaintenance.deleteTask(db, it) } } }
    }
}
