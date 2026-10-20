package com.example.financetracker

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.financetracker.activities.*
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.*
import com.example.financetracker.repository.DataMaintenance
import com.example.financetracker.repository.LoanLedger
import com.example.financetracker.ui.WalletScreen
import com.example.financetracker.utils.CategoryStore
import com.example.financetracker.widget.TodayTasksWidget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DeviceFeaturesTest {
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrument.targetContext
    private val db get() = AppDatabase.getDatabase(context)
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun await(check: () -> Boolean) { repeat(80) { if (check()) return; Thread.sleep(100) }; assertTrue("UI timed out", check()) }
    private fun dialog(): View {
        check(Build.VERSION.SDK_INT >= 29)
        return android.view.inspector.WindowInspector.getGlobalWindowViews().last { it.findViewById<View>(android.R.id.button1) != null }
    }
    private fun shot(name: String) {
        instrument.waitForIdleSync()
        Thread.sleep(600) // Let finite reveal animations settle before reviewing the screenshot.
        val folder = java.io.File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        instrument.uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    @Test fun deletionRemovesOnlyTheChosenDataAndKeepsLoanBalances() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            LoanLedger(database).create("Person", 500.0, true, "Borrowed", LocalDate.now())
            val loan = database.loanDao().getAllLoans().first().single()
            val first = database.taskDao().insert(DailyTask(title = "One", startDate = LocalDate.now().toString())).toInt()
            val second = database.taskDao().insert(DailyTask(title = "Two", startDate = LocalDate.now().toString())).toInt()
            database.taskDao().complete(TaskCompletion(taskId = first, occurrenceDate = LocalDate.now().toString(), title = "One", notes = ""))
            DataMaintenance.deleteTask(database, first)
            assertNull(database.taskDao().task(first)); assertNotNull(database.taskDao().task(second)); assertTrue(database.taskDao().completions().isEmpty())
            DataMaintenance.deleteTransactions(database)
            assertTrue(database.transactionDao().getAllTransactionsRaw().isEmpty())
            assertEquals(500.0, database.loanDao().getById(loan.id)!!.amount, 0.0)
            LoanLedger(database).settle(loan.id, 100.0)
            assertEquals(-100.0, database.transactionDao().getAllTransactionsRaw().single().amount, 0.0)
            DataMaintenance.deleteTasks(database)
            assertTrue(database.taskDao().allTasks().isEmpty()); assertTrue(database.taskDao().completions().isEmpty())
            assertEquals(1, database.transactionDao().getAllTransactionsRaw().size)
        } finally { database.close() }
    }
    @Test fun deletionRequiresBothStepsAndCancelNeverDeletes() {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        var calls = 0
        ActivityScenario.launch<Activity>(Intent(context, SettingsActivity::class.java)).use { scenario ->
            await { var ready = false; scenario.onActivity { ready = it.findViewById<View>(R.id.screenTitle) != null }; ready }
            scenario.onActivity { (it as WalletScreen).confirmDeletion("Delete test?", "Fixture only") { calls++ } }
            instrument.waitForIdleSync()
            instrument.runOnMainSync { dialog().findViewById<View>(android.R.id.button1).performClick() }
            instrument.waitForIdleSync()
            assertEquals(0, calls)
            instrument.runOnMainSync {
                dialog().findViewById<View>(android.R.id.button1).performClick()
                assertEquals(0, calls)
            }
            instrument.waitForIdleSync()
            instrument.runOnMainSync {
                assertNotNull(dialog().findViewById<View>(R.id.deleteVerification))
                dialog().findViewById<View>(android.R.id.button2).performClick()
            }
            assertEquals(0, calls)
            instrument.waitForIdleSync()
            scenario.onActivity { (it as WalletScreen).confirmDeletion("Delete test?", "Fixture only") { calls++ } }
            instrument.waitForIdleSync()
            instrument.runOnMainSync { dialog().findViewById<View>(android.R.id.button1).performClick() }
            instrument.waitForIdleSync()
            instrument.runOnMainSync { dialog().findViewById<android.widget.EditText>(R.id.deleteVerification).setText("DELETE") }
            shot("DeleteConfirmation")
            instrument.runOnMainSync { dialog().findViewById<View>(android.R.id.button1).performClick() }
            assertEquals(1, calls)
        }
    }
    @Test fun typedCategorySurvivesSavingAndIsSuggestedAgain() {
        val prefs = context.getSharedPreferences("finmate_settings", 0)
        val original = prefs.getStringSet("categories", emptySet()).orEmpty().toSet()
        val category = "QA custom ${System.nanoTime()}"
        val title = "QA category record"
        try {
            ActivityScenario.launch<Activity>(Intent(context, AddTransactionActivity::class.java)).use { scenario ->
                await { var ready = false; scenario.onActivity { ready = it.findViewById<View>(R.id.categoryInput) != null }; ready }
                scenario.onActivity { activity ->
                    val all = views(activity.findViewById(android.R.id.content))
                    val fields = all.filterIsInstance<com.google.android.material.textfield.TextInputEditText>()
                    fields.first { it.hint.toString() == "Description" }.setText(title)
                    fields.first { it.hint.toString() == "Amount" }.setText("25.50")
                    activity.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.categoryInput).setText(category, false)
                    all.filterIsInstance<TextView>().first { it.text.toString() == "Save" }.performClick()
                }
                await { CategoryStore.all(context).contains(category) }
            }
            ActivityScenario.launch<Activity>(Intent(context, AddTransactionActivity::class.java)).use { scenario ->
                await { var ready = false; scenario.onActivity { ready = it.findViewById<View>(R.id.categoryInput) != null }; ready }
                scenario.onActivity {
                    val adapter = it.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.categoryInput).adapter
                    assertTrue((0 until adapter.count).any { index -> adapter.getItem(index).toString() == category })
                }
            }
        } finally {
            runBlocking { db.transactionDao().getAllTransactionsRaw().filter { it.title == title && it.category == category }.forEach { db.transactionDao().deleteTransaction(it) } }
            prefs.edit().putStringSet("categories", original).commit()
        }
    }
    @Test fun lightAndDarkThemesKeepFormsReadableAfterRecreation() {
        val prefs = context.getSharedPreferences("finmate_settings", 0)
        val previous = prefs.getString("theme", "system")
        try {
            for (mode in listOf("dark", "light")) {
                prefs.edit().putString("theme", mode).commit()
                instrument.runOnMainSync { WalletApplication.applyTheme(context) }
                ActivityScenario.launch<Activity>(Intent(context, HomeActivity::class.java)).use { scenario ->
                    await { var ready = false; scenario.onActivity { ready = it.findViewById<View>(R.id.periodDate) != null }; ready }
                    scenario.recreate()
                    await { var matches = false; scenario.onActivity { matches = (it as WalletScreen).dark == (mode == "dark") && it.findViewById<View>(R.id.periodDate) != null }; matches }
                    scenario.onActivity {
                        val screen = it as WalletScreen
                        assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(screen.ink, screen.surface) >= 7)
                        assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(screen.muted, screen.surface) >= 4.5)
                    }
                    instrument.waitForIdleSync(); shot("Home_${mode}")
                }
            }
        } finally { prefs.edit().putString("theme", previous).commit(); instrument.runOnMainSync { WalletApplication.applyTheme(context) } }
    }
    private fun withSignedInFixture(work: () -> Unit) {
        val prior = runBlocking { db.userProfileDao().getLoggedInUser() }
        val email = "qa-device-${System.nanoTime()}@example.test"
        runBlocking { db.userProfileDao().logoutAll(); db.userProfileDao().insertOrUpdate(UserProfile(email, "QA", "test-password", true)) }
        try { work() } finally {
            runBlocking { db.openHelper.writableDatabase.execSQL("DELETE FROM user_profiles WHERE email = ?", arrayOf<Any>(email)); prior?.let { db.userProfileDao().update(it) } }
            TodayTasksWidget.refresh(context)
        }
    }
    @Test fun appIconShortcutsOpenFloatingFormsAndLoanMode() = withSignedInFixture {
        val shortcuts = context.getSystemService(android.content.pm.ShortcutManager::class.java).manifestShortcuts
        assertEquals(setOf("add_task", "add_transaction", "add_loan", "today_tasks"), shortcuts.map { it.id }.toSet())
        listOf("task", "transaction", "loan").forEach { kind ->
            instrument.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            instrument.waitForIdleSync(); Thread.sleep(500)
            val type = if (kind == "task") PopupTaskActivity::class.java else PopupTransactionActivity::class.java
            val monitor = instrument.addMonitor(type.name, null, false)
            var popup: Activity? = null
            try {
                context.startActivity(Intent(context, QuickEntryActivity::class.java).setAction("com.example.financetracker.quick.$kind").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                val opened = instrument.waitForMonitorWithTimeout(monitor, 5000)
                popup = opened
                assertNotNull(opened)
                await { var ready = false; instrument.runOnMainSync { ready = opened.findViewById<View>(R.id.screenTitle) != null }; ready }
                instrument.runOnMainSync {
                    val floating = android.util.TypedValue(); opened.theme.resolveAttribute(android.R.attr.windowIsFloating, floating, true)
                    assertTrue(floating.data != 0)
                    assertTrue(opened.window.attributes.height < opened.resources.displayMetrics.heightPixels)
                    assertNull(opened.findViewById<View>(R.id.bottomNav))
                    if (kind == "loan") assertTrue(views(opened.findViewById(android.R.id.content)).filterIsInstance<android.widget.CheckBox>().first { it.text.toString() == "Borrowed or lent" }.isChecked)
                }
                await {
                    var settled = false
                    instrument.runOnMainSync {
                        val form = (opened as WalletScreen).content
                        settled = form.isShown && (0 until form.childCount).all { form.getChildAt(it).alpha > 0.99f }
                    }
                    settled
                }
                shot("Quick_$kind")
            } finally {
                instrument.runOnMainSync { popup?.finish() }; instrument.waitForIdleSync(); Thread.sleep(400)
                instrument.removeMonitor(monitor)
            }
        }
    }
    @Test fun widgetRendersTodayAndTogglesCompletionWithoutOpeningApp() = withSignedInFixture {
        val title = "QA widget ${System.nanoTime()}"
        val id = runBlocking { db.taskDao().insert(DailyTask(title = title, startDate = LocalDate.now().toString(), reminderEnabled = false)).toInt() }
        var host: AppWidgetHost? = null
        try {
            instrument.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
            instrument.runOnMainSync { host = AppWidgetHost(context, 4387) }
            val widgetId = host!!.allocateAppWidgetId()
            val manager = AppWidgetManager.getInstance(context)
            assertTrue(manager.bindAppWidgetIdIfAllowed(widgetId, ComponentName(context, TodayTasksWidget::class.java)))
            runBlocking { TodayTasksWidget.updateAll(context) }
            ActivityScenario.launch<Activity>(Intent(context, HomeActivity::class.java)).use { scenario ->
                await { var ready = false; scenario.onActivity { ready = it.findViewById<View>(R.id.periodDate) != null }; ready }
                scenario.onActivity { activity ->
                    val hosted = host!!.createView(activity, widgetId, manager.getAppWidgetInfo(widgetId))
                    (activity as WalletScreen).content.addView(hosted, 0, android.widget.LinearLayout.LayoutParams(-1, activity.dp(300)))
                    host!!.startListening()
                }
                await { var found = false; scenario.onActivity { found = views(it.findViewById(android.R.id.content)).filterIsInstance<TextView>().any { it.text.contains(title) } }; found }
                shot("TodayTasksWidget")
                scenario.onActivity { activity -> views(activity.findViewById(android.R.id.content)).filterIsInstance<TextView>().first { it.text.contains(title) }.performClick() }
                await { runBlocking { db.taskDao().completions().any { it.taskId == id } } }
            }
        } finally {
            host?.stopListening(); host?.deleteHost(); instrument.uiAutomation.dropShellPermissionIdentity()
            runBlocking { DataMaintenance.deleteTask(db, id) }
        }
    }
}
