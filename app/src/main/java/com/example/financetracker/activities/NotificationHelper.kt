package com.example.financetracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.math.absoluteValue

object NotificationHelper {

    private const val CHANNEL_ID = "budget_channel"
    private const val CHANNEL_NAME = "Budget Notifications"
    private const val CHANNEL_DESC = "Notifications for budget alerts and system actions"

    // 🔔 Create notification channel (Android 8+)
    fun createNotificationChannel(context: Context) {
        com.example.financetracker.utils.NotificationSound.channel(context)
    }

    // 🚨 Budget exceeded notification
    fun showBudgetExceededNotification(context: Context) {
        sendNotification(
            context,
            title = "Budget Exceeded",
            message = "You have exceeded your budget limit."
        )
    }

    // 📢 Reusable notification for any purpose (logout, reset, etc.)
    fun sendNotification(context: Context, title: String, message: String) {
        com.example.financetracker.utils.NotificationHelper.sendNotification(context, title, message)
    }
}
