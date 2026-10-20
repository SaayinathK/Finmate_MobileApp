package com.example.financetracker.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.utils.PlannerRules
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object NotificationScheduler {
    fun preferences(context: Context) = context.getSharedPreferences("finmate_settings", Context.MODE_PRIVATE)
    private fun alarmIntent(context: Context, code: Int, kind: String, taskId: Int = -1): PendingIntent = PendingIntent.getBroadcast(context, code,
        Intent(context, DailyReminderReceiver::class.java).setAction("swallet.reminder.$code").putExtra("kind", kind).putExtra("taskId", taskId).putExtra("code", code),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun schedule(context: Context, code: Int, kind: String, time: LocalDateTime, taskId: Int = -1) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val timestamp = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pending = alarmIntent(context, code, kind, taskId)
        if (android.os.Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) {
            try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp, pending); return }
            catch (_: SecurityException) { /* Permission can be revoked while scheduling. */ }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp, pending)
    }
    fun scheduleDailyReminder(context: Context) {
        val prefs = preferences(context)
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Cancel the pre-upgrade single 9 pm reminder too.
        manager.cancel(PendingIntent.getBroadcast(context, 0, Intent(context, DailyReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        val enabled = prefs.getBoolean("notifications_enabled", true)
        for ((kind, base) in listOf("money" to 100, "checklist" to 200, "creation" to 300)) {
            val count = if (kind == "creation") 1 else 3
            for (index in 0 until count) {
                val code = base + index
                manager.cancel(alarmIntent(context, code, kind))
                if (!enabled || !prefs.getBoolean("${kind}_enabled", true)) continue
                val default = if (kind == "creation") "08:00" else listOf("09:00", "14:00", "21:00")[index]
                val time = runCatching { LocalTime.parse(prefs.getString("${kind}_time_$index", default)) }.getOrDefault(LocalTime.parse(default))
                val now = LocalDateTime.now()
                var next = now.toLocalDate().atTime(time)
                if (next <= now) next = next.plusDays(1)
                schedule(context, code, kind, next)
            }
        }
    }
    suspend fun rescheduleTasks(context: Context) {
        val prefs = preferences(context)
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val previous = prefs.getStringSet("scheduled_task_ids", emptySet()).orEmpty().toSet()
        previous.forEach { id -> manager.cancel(alarmIntent(context, 10000 + id.toInt(), "task", id.toInt())) }
        val ids = mutableSetOf<String>()
        if (prefs.getBoolean("notifications_enabled", true)) {
            val dao = AppDatabase.getDatabase(context).taskDao()
            val completed = dao.completions()
            dao.allTasks().forEach { task ->
                var next = PlannerRules.nextReminder(task, LocalDateTime.now())
                if (next != null && completed.any { it.taskId == task.id && it.occurrenceDate == next!!.toLocalDate().toString() }) {
                    next = PlannerRules.nextReminder(task, next!!.toLocalDate().atTime(23,59,59))
                }
                if (next != null) { schedule(context, 10000 + task.id, "task", next!!, task.id); ids.add(task.id.toString()) }
            }
        }
        prefs.edit().putStringSet("scheduled_task_ids", ids).apply()
        com.example.financetracker.widget.TodayTasksWidget.updateAll(context)
    }
}
