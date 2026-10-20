package com.example.financetracker.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import com.example.financetracker.receiver.NotificationScheduler

/** Channels are immutable after creation. An explicit sound change selects a new channel. */
object NotificationSound {
    fun uri(context: Context): Uri? {
        val prefs = NotificationScheduler.preferences(context)
        if (!prefs.getBoolean("notification_sound", true)) return null
        return when (val value = prefs.getString("notification_tone", "default")) {
            "silent" -> null
            "default", null -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            else -> Uri.parse(value)
        }
    }
    fun channel(context: Context): String {
        val prefs = NotificationScheduler.preferences(context)
        val id = "swallet_alerts_${prefs.getInt("sound_revision", 0)}"
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(id, "SWallet reminders & alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Task deadlines, daily check-ins and budget alerts"
            setSound(uri(context), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(prefs.getBoolean("notification_vibrate", true))
        })
        return id
    }
    fun changed(context: Context) {
        val prefs = NotificationScheduler.preferences(context)
        val old = "swallet_alerts_${prefs.getInt("sound_revision", 0)}"
        prefs.edit().putInt("sound_revision", prefs.getInt("sound_revision", 0) + 1).apply()
        channel(context)
        context.getSystemService(NotificationManager::class.java).deleteNotificationChannel(old)
    }
    fun title(context: Context): String {
        val prefs = NotificationScheduler.preferences(context)
        return when (prefs.getString("notification_tone", "default")) {
            "default" -> "Device default"
            "silent" -> "Silent"
            else -> runCatching { RingtoneManager.getRingtone(context, uri(context))?.getTitle(context) }.getOrNull() ?: "Selected tone"
        }
    }
}
