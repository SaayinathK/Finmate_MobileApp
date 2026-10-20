package com.example.financetracker

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.financetracker.activities.*
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.*
import com.example.financetracker.models.Transaction
import com.example.financetracker.repository.DataMaintenance
import com.example.financetracker.receiver.NotificationScheduler
import com.example.financetracker.utils.NotificationSound
import com.example.financetracker.utils.PlannerRules
import com.google.android.material.chip.Chip
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PlannerFeaturesTest {
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrument.targetContext
    private val db get() = AppDatabase.getDatabase(context)
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun await(check: () -> Boolean) { repeat(100) { if (check()) return; Thread.sleep(100) }; assertTrue("Feature UI timed out", check()) }
    private fun shot(name: String) {
        instrument.waitForIdleSync(); Thread.sleep(300)
        val folder = java.io.File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        instrument.uiAutomation.takeScreenshot()?.let { bitmap -> java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
    }
    @Test fun subtasksHaveIndependentSchedulesAndParentDeletionCleansHistory() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = database.taskDao()
            val parent = dao.insert(DailyTask(title = "Plan trip", startDate = "2026-12-01")).toInt()
            val first = dao.insert(DailyTask(title = "Book tickets", parentTaskId = parent, startDate = "2026-11-01", reminderDate = "2026-10-25", reminderTime = "10:30", deadlineTime = "18:00", remindUntilDone = true)).toInt()
            val second = dao.insert(DailyTask(title = "Pack", parentTaskId = parent, startDate = "2026-11-30", reminderDate = "2026-11-29", reminderTime = "15:00", deadlineTime = "20:00")).toInt()
            dao.complete(TaskCompletion(taskId = first, occurrenceDate = "2026-11-01", title = "Book tickets", notes = ""))
            assertTrue(PlannerRules.isCompleted(dao.task(first)!!, LocalDate.parse("2026-11-02"), dao.completions()))
            assertFalse(PlannerRules.isCompleted(dao.task(second)!!, LocalDate.parse("2026-11-30"), dao.completions()))
            assertEquals(listOf("10:30", "15:00"), dao.children(parent).map { it.reminderTime })
            DataMaintenance.deleteTask(database, parent)
            assertTrue(dao.allTasks().isEmpty()); assertTrue(dao.completions().isEmpty())
        } finally { database.close() }
    }
    @Test fun goalRoundTripPreservesAchievementAndCanBeReopened() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val goal = FutureGoal(title = "Emergency fund", targetDate = "2027-01-01", target = 100000, progress = 25000, unit = "LKR")
            val id = database.goalDao().insert(goal).toInt()
            val achieved = database.goalDao().get(id)!!.copy(progress = 100000, achievedAt = 123456)
            database.goalDao().update(achieved)
            assertEquals(achieved, database.goalDao().get(id))
            database.goalDao().update(achieved.copy(progress = 50000, achievedAt = null))
            assertNull(database.goalDao().get(id)!!.achievedAt)
        } finally { database.close() }
    }
    @Test fun taskEditorRetainsSubtaskAndAfterDaySettingsOnRecreation() {
        val label = "QA subtask ${System.nanoTime()}"
        val parent = runBlocking { db.taskDao().insert(DailyTask(title = label, startDate = LocalDate.now().plusDays(7).toString(), reminderEnabled = false)).toInt() }
        var child = -1
        try {
            ActivityScenario.launch<Activity>(Intent(context, TaskEditorActivity::class.java).putExtra("parentTaskId", parent)).use { scenario ->
                await { var ready = false; scenario.onActivity { ready = it.findViewById<View>(R.id.taskName) != null }; ready }
                scenario.onActivity { activity ->
                    activity.findViewById<EditText>(R.id.taskName).setText(label + " child")
                    views(activity.findViewById(android.R.id.content)).filterIsInstance<Chip>().first { it.text.toString() == "On and after selected day" }.performClick()
                }
                scenario.recreate()
                await { var ready = false; scenario.onActivity { ready = it.findViewById<EditText>(R.id.taskName)?.text?.toString() == label + " child" }; ready }
                shot("SubtaskEditor")
                scenario.onActivity { activity -> views(activity.findViewById(android.R.id.content)).filterIsInstance<Button>().first { it.text.toString() == "Save" }.performClick() }
                await { runBlocking { db.taskDao().children(parent).isNotEmpty() } }
                val saved = runBlocking { db.taskDao().children(parent).single() }; child = saved.id
                assertTrue(saved.remindUntilDone); assertEquals(parent, saved.parentTaskId)
            }
            ActivityScenario.launch<Activity>(Intent(context, TaskEditorActivity::class.java).putExtra("taskId", parent)).use { scenario ->
                await { var ready = false; scenario.onActivity { ready = it.findViewById<android.view.View>(android.R.id.content).findViewWithTag<CheckBox>("subtaskCheck:$child") != null }; ready }
                scenario.onActivity { activity -> activity.findViewById<android.view.View>(android.R.id.content).findViewWithTag<CheckBox>("subtaskCheck:$child").isChecked = true }
                await { runBlocking { db.taskDao().completions().any { it.taskId == child } } }
            }
        } finally { runBlocking { DataMaintenance.deleteTask(db, parent); NotificationScheduler.rescheduleTasks(context) } }
    }
    @Test fun suggestionsFillWithoutSavingAndScreensRender() {
        val label = "QA suggested ${System.nanoTime()}"
        runBlocking { db.transactionDao().insertAll((0 until 20).map {
            Transaction(title = label, amount = -125.0, category = "Transport", date = PlannerRules.displayDate(LocalDate.now().minusDays(it.toLong() * 7)), type = "Expense")
        }) }
        try {
            val count = runBlocking { db.transactionDao().getAllTransactionsRaw().size }
            ActivityScenario.launch<Activity>(Intent(context, AddTransactionActivity::class.java)).use { scenario ->
                await { var ready = false; scenario.onActivity { ready = views(it.findViewById(android.R.id.content)).filterIsInstance<Button>().any { view -> view.text.toString().startsWith(label) } }; ready }
                scenario.onActivity { activity -> views(activity.findViewById(android.R.id.content)).filterIsInstance<Button>().first { it.text.toString().startsWith(label) }.performClick() }
                scenario.onActivity { activity -> assertTrue(views(activity.findViewById(android.R.id.content)).filterIsInstance<EditText>().any { it.text.toString() == "125.00" }) }
            }
            assertEquals(count, runBlocking { db.transactionDao().getAllTransactionsRaw().size })
            for ((clazz, title) in listOf(ProfileActivity::class.java to "Profile", GoalsActivity::class.java to "Goals & achievements", ReminderSettingsActivity::class.java to "Reminders")) {
                ActivityScenario.launch<Activity>(Intent(context, clazz)).use { scenario ->
                    await { var ready = false; scenario.onActivity { ready = it.findViewById<android.widget.TextView>(R.id.screenTitle)?.text?.toString() == title }; ready }
                    shot(clazz.simpleName)
                }
            }
        } finally { runBlocking { db.transactionDao().getAllTransactionsRaw().filter { it.title == label }.forEach { db.transactionDao().deleteTransaction(it) } } }
    }
    @Test fun soundChangesCreateChannelWithSelectedSound() {
        val prefs = NotificationScheduler.preferences(context)
        val before = prefs.getBoolean("notification_sound", true)
        try {
            prefs.edit().putBoolean("notification_sound", false).commit(); NotificationSound.changed(context)
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            assertNull(manager.getNotificationChannel(NotificationSound.channel(context)).sound)
            prefs.edit().putBoolean("notification_sound", true).commit(); NotificationSound.changed(context)
            assertEquals(NotificationSound.uri(context), manager.getNotificationChannel(NotificationSound.channel(context)).sound)
        } finally { prefs.edit().putBoolean("notification_sound", before).commit(); NotificationSound.changed(context) }
    }
    @Test fun profileContainsSettingsAndToolsAndHomeDoesNotRepeatShortcuts() {
        val toolNames = setOf("Reports", "Loans", "Goals & achievements", "Reminders", "Excel")
        for (clazz in listOf(ProfileActivity::class.java, SettingsActivity::class.java)) {
            ActivityScenario.launch<Activity>(Intent(context, clazz)).use { scenario ->
                await { var ready = false; scenario.onActivity { activity -> ready = views(activity.findViewById(android.R.id.content)).filterIsInstance<Button>().any { it.text.toString().startsWith("Theme:") } }; ready }
                scenario.onActivity { activity ->
                    val labels = views(activity.findViewById(android.R.id.content)).filterIsInstance<Button>().map { it.text.toString() }
                    assertTrue(labels.containsAll(toolNames))
                    assertTrue(labels.any { it.startsWith("Currency:") })
                    assertTrue(labels.contains("Delete all tasks")); assertTrue(labels.contains("Today's tasks widget"))
                    assertFalse(labels.contains("View profile")); assertFalse(labels.contains("Account & security settings"))
                    assertEquals("Profile", activity.findViewById<android.widget.TextView>(R.id.screenTitle).text.toString())
                }
            }
        }
        ActivityScenario.launch<Activity>(Intent(context, HomeActivity::class.java)).use { scenario ->
            await { var ready = false; scenario.onActivity { ready = views(it.findViewById(android.R.id.content)).filterIsInstance<Button>().any { view -> view.text.toString() == "Edit budget" } }; ready }
            scenario.onActivity { activity ->
                val labels = views(activity.findViewById(android.R.id.content)).filterIsInstance<Button>().map { it.text.toString() }.toSet()
                assertTrue(labels.intersect(toolNames + "Profile").isEmpty())
            }
        }
    }
}
