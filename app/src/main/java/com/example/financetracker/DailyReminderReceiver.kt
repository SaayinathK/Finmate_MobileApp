package com.example.financetracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class DailyReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // For Android 8.0 and above, you must create a notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "daily_reminder",
                "Daily Expense Reminder",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminder to add your daily expenses"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, "daily_reminder")
            .setSmallIcon(R.drawable.ic_notification) // Make sure you have this icon in your drawable
            .setContentTitle("Track Your Spending")
            .setContentText("Don't forget to add today's transactions!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        notificationManager.notify(2, builder.build())
    }
}
