package com.example.financetracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object BudgetNotifier {

    fun checkAndNotify(context: Context, currentSpending: Double, budgetLimit: Double) {
        if (currentSpending > budgetLimit) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Create notification channel for Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "budget_alerts",
                    "Budget Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifies when budget is exceeded"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val builder = NotificationCompat.Builder(context, "budget_alerts")
                .setSmallIcon(R.drawable.ic_warning) // ✅ Vector icon you added
                .setContentTitle("Budget Limit Exceeded")
                .setContentText("You've exceeded your monthly budget!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            notificationManager.notify(1, builder.build())
        }
    }
}
