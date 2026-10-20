package com.example.financetracker

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class WalletApplication : Application() {
    override fun onCreate() { super.onCreate(); applyTheme(this); com.example.financetracker.security.AppLock.install(this) }
    companion object {
        fun applyTheme(context: Context) {
            val mode = context.getSharedPreferences("finmate_settings", Context.MODE_PRIVATE).getString("theme", "system")
            AppCompatDelegate.setDefaultNightMode(when (mode) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            })
        }
    }
}
