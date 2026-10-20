package com.example.financetracker

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.DailyTask
import com.example.financetracker.models.TaskCompletion
import com.example.financetracker.repository.LoanLedger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class WalletIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun migrationPreservesVersionSixTasksAndStartsUnselected() = runBlocking {
        val name = "wallet-v6-task-migration.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { old ->
            old.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, amount REAL NOT NULL, category TEXT NOT NULL, date TEXT NOT NULL, type TEXT NOT NULL, loanId INTEGER, loanEvent TEXT, splitGroup TEXT)")
            old.execSQL("CREATE TABLE loans (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, personName TEXT NOT NULL, amount REAL NOT NULL, type TEXT NOT NULL, note TEXT, date INTEGER NOT NULL, originalAmount REAL NOT NULL DEFAULT 0)")
            old.execSQL("CREATE TABLE budget (budgetKey TEXT PRIMARY KEY NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL)")
            old.execSQL("CREATE TABLE user_profiles (email TEXT PRIMARY KEY NOT NULL, fullName TEXT NOT NULL, password TEXT NOT NULL, isLoggedIn INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE daily_tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, notes TEXT NOT NULL, startDate TEXT NOT NULL, recurrence TEXT NOT NULL, weekdays TEXT NOT NULL, reminderTime TEXT NOT NULL, reminderEnabled INTEGER NOT NULL, active INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE task_completions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskId INTEGER NOT NULL, occurrenceDate TEXT NOT NULL, completedAt INTEGER NOT NULL, title TEXT NOT NULL, notes TEXT NOT NULL)")
            old.execSQL("CREATE UNIQUE INDEX index_task_completions_taskId_occurrenceDate ON task_completions (taskId, occurrenceDate)")
            old.execSQL("INSERT INTO daily_tasks VALUES (1,'Exam','Full notes','2026-10-19','WEEKLY','1,5','17:35',1,1,123)")
            old.execSQL("INSERT INTO task_completions VALUES (1,1,'2026-10-19',456,'Exam snapshot','Original notes')")
            old.version = 6
        }
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9).build()
        try {
            val task = database.taskDao().task(1)!!
            assertEquals("Exam", task.title); assertEquals("Full notes", task.notes)
            assertEquals("1,5", task.weekdays); assertEquals("17:35", task.reminderTime)
            assertFalse(task.important); assertTrue(task.reminderEnabled)
            assertNull(task.parentTaskId); assertNull(task.reminderDate); assertFalse(task.remindUntilDone)
            assertEquals("23:59", task.deadlineTime)
            assertTrue(database.goalDao().all().isEmpty())
            assertEquals("Exam snapshot", database.taskDao().completions().single().title)
            database.taskDao().update(task.copy(important = true))
            assertTrue(database.taskDao().task(1)!!.important)
        } finally { database.close(); context.deleteDatabase(name) }
    }
    @Test fun migrationPreservesVersionFiveAccounts() = runBlocking {
        val name = "wallet-migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { old ->
            old.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, amount REAL NOT NULL, category TEXT NOT NULL, date TEXT NOT NULL, type TEXT NOT NULL)")
            old.execSQL("CREATE TABLE loans (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, personName TEXT NOT NULL, amount REAL NOT NULL, type TEXT NOT NULL, note TEXT, date INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE budget (budgetKey TEXT PRIMARY KEY NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL)")
            old.execSQL("CREATE TABLE user_profiles (email TEXT PRIMARY KEY NOT NULL, fullName TEXT NOT NULL, password TEXT NOT NULL, isLoggedIn INTEGER NOT NULL)")
            old.execSQL("INSERT INTO transactions VALUES (1,'Petrol',-1000,'Transport','19 Oct 2026','Expense')")
            old.execSQL("INSERT INTO loans VALUES (1,'Amma',1500,'RECEIVED','Borrowed',1)")
            old.execSQL("INSERT INTO budget VALUES ('2026_October',20000,'LKR')")
            old.version = 5
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9).build()
        try {
            assertEquals("Petrol", db.transactionDao().getAllTransactionsRaw().single().title)
            assertEquals(1500.0, db.loanDao().getById(1)!!.originalAmount, 0.0)
            assertNull(db.loanDao().getById(1)!!.sharedBillGroup)
            assertEquals(20000.0, db.budgetDao().getBudgetByKey("2026_October")!!.amount, 0.0)
            assertTrue(db.taskDao().allTasks().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun bothLoanDirectionsAndPartialRepaymentsKeepCorrectCashFlow() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val ledger = LoanLedger(db)
            ledger.create("Amma",1000.0,true,"Borrowed",LocalDate.of(2026,10,19))
            val borrowed = db.loanDao().getAllLoans().first().single()
            ledger.settle(borrowed.id,250.0)
            assertEquals(750.0,db.loanDao().getById(borrowed.id)!!.amount,0.0)
            var entries = db.transactionDao().getAllTransactionsRaw()
            assertEquals(-250.0,entries.first { it.loanEvent == "SETTLE" }.amount,0.0)
            assertEquals("Expense",entries.first { it.loanEvent == "SETTLE" }.type)
            try { ledger.settle(borrowed.id,751.0); fail("Overpayment must fail") } catch (_: IllegalArgumentException) { }
            assertEquals(2,db.transactionDao().getAllTransactionsRaw().size)
            ledger.settle(borrowed.id,750.0)
            assertEquals(0.0,db.loanDao().getById(borrowed.id)!!.amount,0.0)
            ledger.create("Friend",600.0,false,"Lent",LocalDate.of(2026,10,19))
            val lent = db.loanDao().getAllLoans().first().first { it.personName == "Friend" }
            ledger.settle(lent.id,200.0)
            entries = db.transactionDao().getAllTransactionsRaw()
            assertEquals(400.0,db.loanDao().getById(lent.id)!!.amount,0.0)
            assertEquals("Income",entries.first { it.loanId == lent.id && it.loanEvent == "SETTLE" }.type)
            assertEquals(-600.0,entries.first { it.loanId == lent.id && it.loanEvent == "OPEN" }.amount,0.0)
        } finally { db.close() }
    }
    @Test fun taskHistoryIsUniquePerDayAndPreservesOriginalDetails() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.taskDao()
            val task = DailyTask(title = "Daily work", notes = "Original details", startDate = "2026-10-19", recurrence = "DAILY")
            val id = dao.insert(task).toInt()
            val completion = TaskCompletion(taskId = id, occurrenceDate = "2026-10-19", title = task.title, notes = task.notes)
            dao.complete(completion); dao.complete(completion)
            dao.complete(completion.copy(occurrenceDate = "2026-10-20"))
            dao.update(task.copy(id = id, title = "New name", notes = "New notes", active = false))
            assertEquals(2,dao.completions().size)
            assertTrue(dao.completions().all { it.title == "Daily work" && it.notes == "Original details" })
            dao.undo(id,"2026-10-19"); assertEquals(1,dao.completions().size)
        } finally { db.close() }
    }
}
