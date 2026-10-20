package com.example.financetracker.receiver

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.financetracker.R
import com.example.financetracker.activities.AddTransactionActivity
import com.example.financetracker.activities.TaskEditorActivity
import com.example.financetracker.activities.TasksActivity
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.utils.PlannerRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = NotificationScheduler.preferences(context)
                val kind = intent?.getStringExtra("kind") ?: "money"
                if (!prefs.getBoolean("notifications_enabled", true)) return@launch
                if (kind != "task" && !prefs.getBoolean("${kind}_enabled", true)) return@launch
                val dao = AppDatabase.getDatabase(context).taskDao()
                val today = LocalDate.now()
                var title = "Time for a quick money check-in"
                var message = "Add today's income and expenses. A minute now makes things clearer later."
                var target: Class<*> = AddTransactionActivity::class.java
                if (kind == "checklist") {
                    val completed = dao.completions()
                    val remaining = dao.allTasks().count { task -> PlannerRules.occurs(task, today, completed) && !PlannerRules.isCompleted(task, today, completed) }
                    title = "Your checklist is ready"
                    message = if (remaining == 0) "Review today's checklist or plan your next task." else "$remaining tasks left on today's checklist. Take the next small step."
                    target = TasksActivity::class.java
                } else if (kind == "creation") {
                    title = "Make a little plan for today"
                    message = "Add your work, errands and priorities to today's checklist."
                    target = TaskEditorActivity::class.java
                } else if (kind == "task") {
                    val id = intent?.getIntExtra("taskId", -1) ?: -1
                    val task = dao.task(id) ?: return@launch
                    val validDay = if (task.recurrence == "ONCE") {
                        val first = LocalDate.parse(task.reminderDate ?: task.startDate)
                        today == first || (task.remindUntilDone && today > first)
                    } else PlannerRules.occurs(task, today)
                    if (!task.active || !task.reminderEnabled || !validDay || PlannerRules.isCompleted(task, today, dao.completions())) return@launch
                    title = task.title; message = task.notes.ifBlank { "A little nudge for your scheduled task." }; target = TasksActivity::class.java
                }
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return@launch
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = com.example.financetracker.utils.NotificationSound.channel(context)
                val code = intent?.getIntExtra("code", 100) ?: 100
                if (kind == "task") target = TaskEditorActivity::class.java
                val destination = Intent(context, target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (kind == "task") destination.putExtra("taskId", intent?.getIntExtra("taskId", -1) ?: -1)
                val tap = PendingIntent.getActivity(context, code, destination, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                manager.notify(code, NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setContentIntent(tap).setAutoCancel(true).build())
            } finally {
                try { NotificationScheduler.scheduleDailyReminder(context); NotificationScheduler.rescheduleTasks(context) }
                finally { pending.finish() }
            }
        }
    }
}
