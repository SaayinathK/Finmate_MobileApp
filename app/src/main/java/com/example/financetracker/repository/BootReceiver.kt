package com.example.financetracker.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.financetracker.receiver.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { NotificationScheduler.scheduleDailyReminder(context); NotificationScheduler.rescheduleTasks(context) }
            finally { pending.finish() }
        }
    }
}
