package com.example.financetracker.activities

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.CheckBox
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.receiver.NotificationScheduler
import com.example.financetracker.ui.WalletScreen
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ReminderSettingsActivity : WalletScreen() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }
    override fun onResume() { super.onResume(); update(); render() }
    private fun update() {
        NotificationScheduler.scheduleDailyReminder(this)
        lifecycleScope.launch { NotificationScheduler.rescheduleTasks(this@ReminderSettingsActivity) }
    }
    private fun render() {
        screen("Reminders", nav = false)
        val prefs = NotificationScheduler.preferences(this)
        val permission = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val status = card()
        status.addView(text(if (permission) "Notifications on" else "Notifications off", 17, if (permission) green else red, true))
        if (!permission) button("Allow notifications", status) {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                (!prefs.getBoolean("notification_permission_requested", false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
                prefs.edit().putBoolean("notification_permission_requested", true).apply()
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 41)
            } else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
        val master = CheckBox(this).apply { text = "Enable reminders"; isChecked = prefs.getBoolean("notifications_enabled", true); minHeight = dp(48)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("notifications_enabled", checked).apply(); update(); render() } }
        status.addView(master)
        val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
        val precise = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
        status.addView(text(if (precise) "Precise timing on" else "Standard timing; delays possible", 13, muted))
        if (!precise && Build.VERSION.SDK_INT >= 31) button("Enable precise timing", status, true) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:$packageName")))
        }
        for ((kind, label) in listOf("money" to "Transactions", "checklist" to "Checklist", "creation" to "Task planning")) {
            val c = card()
            val heading = row()
            heading.addView(column().apply { addView(text(label, 16, ink, true)); addView(text(if (kind == "creation") "Daily" else "3 times daily", 12, muted)) }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            val switch = CheckBox(this).apply { contentDescription = "$label reminders"; isChecked = prefs.getBoolean("${kind}_enabled", true); minHeight = dp(48); minimumWidth = dp(48); isEnabled = master.isChecked
                setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("${kind}_enabled", checked).apply(); update(); render() } }
            heading.addView(switch); c.addView(heading)
            val times = row()
            repeat(if (kind == "creation") 1 else 3) { index ->
                val default = if (kind == "creation") "08:00" else listOf("09:00", "14:00", "21:00")[index]
                val time = LocalTime.parse(prefs.getString("${kind}_time_$index", default))
                button(time.format(DateTimeFormatter.ofPattern("h:mm a")), times, true) {
                    TimePickerDialog(this, { _, h, m ->
                        val selected = LocalTime.of(h,m).toString()
                        val duplicate = (0 until (if (kind == "creation") 1 else 3)).any { it != index && prefs.getString("${kind}_time_$it", listOf("09:00", "14:00", "21:00")[it]) == selected }
                        if (duplicate) toast("Choose a different time for each check-in") else { prefs.edit().putString("${kind}_time_$index", selected).apply(); update(); render() }
                    }, time.hour, time.minute, false).show()
                }.apply {
                    isEnabled = master.isChecked && switch.isChecked; textSize = 12f
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) marginStart = dp(4) }
                }
            }
            c.addView(times)
        }
        content.addView(text("Set individual alerts in each task.", 13, muted))
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41) { update(); render(); if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) toast("You can allow notifications from Android app settings.") }
    }
}
