package com.example.financetracker

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.financetracker.activities.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import android.view.View
import android.view.ViewGroup

@RunWith(AndroidJUnit4::class)
class WalletScreenTest {
    @org.junit.Before fun allowNotifications() {
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant com.example.financetracker android.permission.POST_NOTIFICATIONS").close()
    }
    private fun await(check: () -> Boolean) {
        repeat(60) { if (check()) return; Thread.sleep(100) }
        assertTrue("UI did not reach the expected state", check())
    }
    private fun descendants(root: View): List<View> = listOf(root) + if (root is ViewGroup) (0 until root.childCount).flatMap { descendants(root.getChildAt(it)) } else emptyList()

    @Test fun addButtonsStayAboveNavigationAndOpenTheRightForms() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val routes = listOf(
            Triple(HomeActivity::class.java, AddTransactionActivity::class.java, "Add transaction"),
            Triple(TransactionHistoryActivity::class.java, AddTransactionActivity::class.java, "Add transaction"),
            Triple(LoansActivity::class.java, AddTransactionActivity::class.java, "Add loan"),
            Triple(TasksActivity::class.java, TaskEditorActivity::class.java, "New task")
        )
        routes.forEach { (source, destination, title) ->
            ActivityScenario.launch<android.app.Activity>(Intent(instrumentation.targetContext, source)).use { scenario ->
                await { var ready = false; scenario.onActivity { ready = it.findViewById<View>(R.id.addAction)?.isLaidOut == true }; ready }
                scenario.onActivity { activity ->
                    activity.findViewById<android.widget.ScrollView>(R.id.screenScroll).fullScroll(View.FOCUS_DOWN)
                }
                instrumentation.waitForIdleSync()
                val monitor = instrumentation.addMonitor(destination.name, null, false)
                try {
                    scenario.onActivity { activity ->
                        val fab = activity.findViewById<View>(R.id.addAction)
                        val navigation = activity.findViewById<View>(R.id.bottomNav)
                        val fabRect = android.graphics.Rect(); val navRect = android.graphics.Rect()
                        assertTrue(fab.getGlobalVisibleRect(fabRect)); assertTrue(navigation.getGlobalVisibleRect(navRect))
                        assertTrue("Add button must remain above navigation", fabRect.bottom <= navRect.top)
                        assertTrue("Add button must be on the right", fabRect.centerX() > activity.resources.displayMetrics.widthPixels / 2)
                        fab.performClick()
                    }
                    val opened = instrumentation.waitForMonitorWithTimeout(monitor, 5000)
                    assertNotNull("Floating action must open ${destination.simpleName}", opened)
                    await {
                        var ready = false
                        instrumentation.runOnMainSync { ready = opened.findViewById<android.widget.TextView>(R.id.screenTitle)?.text?.toString() == title }
                        ready
                    }
                    instrumentation.runOnMainSync {
                        if (source == LoansActivity::class.java) {
                            val checkbox = descendants(opened.findViewById(android.R.id.content)).filterIsInstance<android.widget.CheckBox>().first { it.text.toString() == "Borrowed or lent" }
                            assertTrue("New loan must open in loan mode", checkbox.isChecked)
                        }
                        opened.finish()
                    }
                } finally { instrumentation.removeMonitor(monitor) }
            }
        }
    }

    @Test fun monthAndYearPickerAppliesSelectionAndKeepsNavigationWorking() {
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= 29)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch<android.app.Activity>(Intent(instrumentation.targetContext, ReportsActivity::class.java)).use { scenario ->
            await { var ready = false; scenario.onActivity { ready = it.findViewById<View>(R.id.periodDate)?.isLaidOut == true }; ready }
            scenario.onActivity { it.findViewById<View>(R.id.periodDate).performClick() }
            val targetYear = java.time.LocalDate.now().year + 1
            instrumentation.runOnMainSync {
                if (Build.VERSION.SDK_INT >= 29) {
                    val dialog = android.view.inspector.WindowInspector.getGlobalWindowViews().last { it.findViewById<View>(R.id.monthYearPickerYear) != null }
                    dialog.findViewById<android.widget.NumberPicker>(R.id.monthYearPickerYear).value = targetYear
                    descendants(dialog).filterIsInstance<android.widget.Button>().first { it.text.toString() == java.text.DateFormatSymbols.getInstance().shortMonths[1] }.performClick()
                }
            }
            instrumentation.waitForIdleSync()
            val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(folder, "MonthYearPicker.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
            instrumentation.runOnMainSync {
                if (Build.VERSION.SDK_INT >= 29) {
                    android.view.inspector.WindowInspector.getGlobalWindowViews().last { it.findViewById<View>(R.id.monthYearPickerYear) != null }.findViewById<View>(android.R.id.button1).performClick()
                }
            }
            fun selected(month: Int): Boolean {
                var matches = false
                scenario.onActivity { activity ->
                    val label = java.time.YearMonth.of(targetYear, month).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
                    matches = activity.findViewById<View>(R.id.periodDate)?.contentDescription?.contains(label) == true
                }
                return matches
            }
            await { selected(2) }
            scenario.onActivity { activity -> descendants(activity.findViewById(android.R.id.content)).first { it.contentDescription?.toString() == "Next month" }.performClick() }
            await { selected(3) }
        }
    }

    @Test fun mainScreensOpenAndSurviveRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.executeShellCommand("pm grant com.example.financetracker android.permission.POST_NOTIFICATIONS").close()
        val classes = listOf(HomeActivity::class.java, AddTransactionActivity::class.java, TasksActivity::class.java, TaskEditorActivity::class.java,
            LoansActivity::class.java, ReportsActivity::class.java, TransactionHistoryActivity::class.java, ReminderSettingsActivity::class.java, SettingsActivity::class.java,
            LoginActivity::class.java, SignupActivity::class.java, BudgetSetupActivity::class.java, ExportImportActivity::class.java)
        classes.forEach { activityClass ->
            ActivityScenario.launch<android.app.Activity>(Intent(instrumentation.targetContext, activityClass)).use { scenario ->
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity -> assertNotNull(activity.findViewById<android.view.View>(android.R.id.content)) }
                scenario.recreate()
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity -> assertFalse(activity.isFinishing) }
                Thread.sleep(400)
                val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    java.io.File(folder, "${activityClass.simpleName}.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
        }
    }
}
