package com.example.financetracker.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.databinding.ActivitySplashBinding
import com.example.financetracker.receiver.NotificationScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.logoImage.alpha = 0f
        binding.logoImage.scaleX = 0.94f; binding.logoImage.scaleY = 0.94f
        binding.logoImage.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(500).start()
        lifecycleScope.launch {
            NotificationScheduler.scheduleDailyReminder(this@SplashActivity)
            NotificationScheduler.rescheduleTasks(this@SplashActivity)
            delay(650)
            startActivity(Intent(this@SplashActivity, LoginActivity::class.java)); finish()
        }
    }
}
