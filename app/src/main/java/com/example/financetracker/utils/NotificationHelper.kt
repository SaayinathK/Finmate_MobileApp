package com.example.financetracker.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.financetracker.R
import kotlin.math.absoluteValue

object NotificationHelper {

    private const val CHANNEL_ID = "budget_channel"
    private const val CHANNEL_NAME = "Budget Notifications"
    private const val CHANNEL_DESC = "Notifications for budget alerts and system actions"

    // 🔔 Create notification channel (Android 8+)
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
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
        if (!context.getSharedPreferences("finmate_settings", Context.MODE_PRIVATE).getBoolean("notifications_enabled", true)) return
        if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // Make sure this exists in res/drawable
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationId = (title + message).hashCode().absoluteValue

        NotificationManagerCompat.from(context)
            .notify(notificationId, builder.build())
    }
}
