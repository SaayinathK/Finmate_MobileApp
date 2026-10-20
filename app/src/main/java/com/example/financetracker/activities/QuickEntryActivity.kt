package com.example.financetracker.activities

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.database.AppDatabase
import kotlinx.coroutines.launch

/** External entry points live in a separate task; normal in-app forms stay full screen. */
class QuickEntryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = intent.action?.substringAfterLast('.')
        if (kind !in setOf("task", "loan", "transaction", "today")) { finish(); return }
        lifecycleScope.launch {
            if (AppDatabase.getDatabase(this@QuickEntryActivity).userProfileDao().getLoggedInUser() == null) {
                startActivity(Intent(this@QuickEntryActivity, LoginActivity::class.java).putExtra("pendingQuickAction", kind))
            } else {
                val target = when (kind) {
                    "task" -> PopupTaskActivity::class.java
                    "today" -> TasksActivity::class.java
                    else -> PopupTransactionActivity::class.java
                }
                startActivity(Intent(this@QuickEntryActivity, target).putExtra("createLoan", kind == "loan"))
            }
            finish()
        }
    }
}

internal fun android.app.Activity.sizeQuickWindow() {
    window.setLayout((resources.displayMetrics.widthPixels * 0.94f).toInt(), (resources.displayMetrics.heightPixels * 0.86f).toInt())
    window.setGravity(Gravity.CENTER)
    window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
}

class PopupTaskActivity : TaskEditorActivity() {
    override fun onStart() { super.onStart(); sizeQuickWindow() }
}
class PopupTransactionActivity : AddTransactionActivity() {
    override fun onStart() { super.onStart(); sizeQuickWindow() }
}
