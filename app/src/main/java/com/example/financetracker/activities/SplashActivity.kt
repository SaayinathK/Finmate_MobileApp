package com.example.financetracker.activities

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.financetracker.receiver.DailyReminderReceiver
import com.example.financetracker.databinding.ActivitySplashBinding
import java.util.Calendar

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Attach standard bottom navigation if present in layout
            try {
            com.example.financetracker.ui.BottomNavHelper.attach(this)
        } catch (ex: Throwable) {
            android.util.Log.e("SplashActivity", "Error attaching BottomNavHelper", ex)
        }

        // Schedule daily reminder (guard against any unexpected exceptions)
        try {
            scheduleDailyReminder(this)
        } catch (ex: Throwable) {
            android.util.Log.e("SplashActivity", "Error scheduling daily reminder", ex)
        }

        // Delay splash and move to home
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }, 2500) // 2.5 seconds splash delay
    }

    private fun scheduleDailyReminder(context: Context) {
        val intent = Intent(context, DailyReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21) // 9 PM
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }
}
