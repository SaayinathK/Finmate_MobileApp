package com.example.financetracker

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.financetracker.activities.AddTransactionActivity
import com.example.financetracker.activities.ReportsActivity
import com.example.financetracker.database.AppDatabase
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionEntryTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val db get() = AppDatabase.getDatabase(instrumentation.targetContext)
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun await(check: () -> Boolean) { repeat(80) { if (check()) return; Thread.sleep(100) }; assertTrue("Timed out waiting for saved transaction", check()) }
    private fun form(activity: Activity, title: String, amount: String, category: String) {
        val fields = views(activity.findViewById(android.R.id.content)).filterIsInstance<TextInputEditText>()
        fields.first { it.hint.toString() == "Description" }.setText(title)
        fields.first { it.hint.toString().equals("Amount") }.setText(amount)
        activity.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.categoryInput).setText(category, false)
    }
    @Test fun reportsRenderAllMoneyAndTaskChartsWithRealDatabaseRecords() {
        val label = "QA report ${System.nanoTime()}"
        var taskId = -1
        try {
            runBlocking {
                val date = java.time.LocalDate.now()
                db.transactionDao().insertAll(listOf(
                    com.example.financetracker.models.Transaction(title = label, amount = 5000.0, category = "Salary", date = com.example.financetracker.utils.PlannerRules.displayDate(date), type = "Income"),
                    com.example.financetracker.models.Transaction(title = label, amount = -1250.0, category = "Food", date = com.example.financetracker.utils.PlannerRules.displayDate(date), type = "Expense")
                ))
                taskId = db.taskDao().insert(com.example.financetracker.models.DailyTask(title = label, startDate = date.toString(), reminderEnabled = false)).toInt()
                db.taskDao().complete(com.example.financetracker.models.TaskCompletion(taskId = taskId, occurrenceDate = date.toString(), title = label, notes = "Report test"))
            }
            ActivityScenario.launch<Activity>(Intent(instrumentation.targetContext, ReportsActivity::class.java)).use { scenario ->
                await {
                    var count = 0
                    scenario.onActivity { activity -> count = views(activity.findViewById(android.R.id.content)).count { it is com.github.mikephil.charting.charts.Chart<*> } }
                    count == 4
                }
                instrumentation.waitForIdleSync()
            }
        } finally {
            runBlocking {
                db.transactionDao().getAllTransactionsRaw().filter { it.title == label }.forEach { db.transactionDao().deleteTransaction(it) }
                if (taskId > 0) {
                    db.taskDao().undo(taskId, java.time.LocalDate.now().toString())
                    db.openHelper.writableDatabase.execSQL("DELETE FROM daily_tasks WHERE id = ?", arrayOf<Any>(taskId))
                }
            }
        }
    }
    @Test fun customCategorySplitsSaveOnlyTheTotalAndPreserveDirection() {
        val title = "QA split ${System.nanoTime()}"
        try {
            ActivityScenario.launch<Activity>(Intent(instrumentation.targetContext, AddTransactionActivity::class.java)).use { scenario ->
                await { var ready = false; scenario.onActivity { ready = views(it.findViewById(android.R.id.content)).filterIsInstance<TextInputEditText>().size >= 8 }; ready }
                scenario.onActivity { activity ->
                    form(activity, title,"100.00","Other")
                    val all = views(activity.findViewById(android.R.id.content))
                    all.filterIsInstance<TextView>().first { it.text.toString() == "Categories" }.performClick()
                    val fields = all.filterIsInstance<TextInputEditText>()
                    fields.first { it.hint.toString() == "Category 1" }.setText("Food")
                    fields.first { it.hint.toString() == "Category 2" }.setText("Transport")
                    fields.filter { it.hint.toString() == "Share amount" }.let { it[0].setText("30.25"); it[1].setText("69.75") }
                    all.filterIsInstance<TextView>().first { it.text.toString() == "Save" }.performClick()
                }
                await { runBlocking { db.transactionDao().getAllTransactionsRaw().count { it.title == title } == 2 } }
            }
            val entries = runBlocking { db.transactionDao().getAllTransactionsRaw().filter { it.title == title } }
            assertEquals(-100.0,entries.sumOf { it.amount },0.0)
            assertTrue(entries.all { it.type == "Expense" && it.splitGroup != null })
            assertEquals(setOf("Food","Transport"),entries.map { it.category }.toSet())
        } finally { runBlocking { db.transactionDao().getAllTransactionsRaw().filter { it.title == title }.forEach { db.transactionDao().deleteTransaction(it) } } }
    }
    @Test fun borrowedMoneyFormCreatesOneIncomeAndOnePersonAccount() {
        val title = "QA borrowed ${System.nanoTime()}"
        val person = "QA person ${System.nanoTime()}"
        try {
            ActivityScenario.launch<Activity>(Intent(instrumentation.targetContext, AddTransactionActivity::class.java)).use { scenario ->
                await { var ready = false; scenario.onActivity { ready = views(it.findViewById(android.R.id.content)).filterIsInstance<TextInputEditText>().size >= 8 }; ready }
                scenario.onActivity { activity ->
                    form(activity,title,"1000.00","Loans")
                    val all = views(activity.findViewById(android.R.id.content))
                    all.filterIsInstance<CheckBox>().first { it.text.toString() == "Borrowed or lent" }.isChecked = true
                    all.filterIsInstance<TextInputEditText>().first { it.hint.toString() == "Person" }.setText(person)
                    all.filterIsInstance<TextView>().first { it.text.toString() == "Save" }.performClick()
                }
                await { runBlocking { db.transactionDao().getAllTransactionsRaw().any { it.title == title } } }
            }
            val tx = runBlocking { db.transactionDao().getAllTransactionsRaw().single { it.title == title } }
            assertEquals("Income",tx.type); assertEquals(1000.0,tx.amount,0.0)
            val loan = runBlocking { db.loanDao().getById(tx.loanId!!)!! }
            assertEquals(person,loan.personName); assertEquals(1000.0,loan.amount,0.0)
        } finally {
            runBlocking { db.transactionDao().getAllTransactionsRaw().filter { it.title == title }.forEach { tx -> db.transactionDao().deleteTransaction(tx); tx.loanId?.let { db.loanDao().deleteLoanById(it) } } }
        }
    }
}
