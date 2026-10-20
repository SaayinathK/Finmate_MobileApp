package com.example.financetracker

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class WalletApplication : Application() {
    override fun onCreate() { super.onCreate(); applyTheme(this); com.example.financetracker.security.AppLock.install(this) }
    companion object {
        fun applyTheme(context: Context) {
            val mode = context.getSharedPreferences("finmate_settings", Context.MODE_PRIVATE).getString("theme", "system")
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                // Persist the app's choice for the system-drawn launch screen as well.
                // AUTO clears the per-app night override, allowing System to follow device changes.
                context.getSystemService(android.app.UiModeManager::class.java).setApplicationNightMode(when (mode) {
                    "light" -> android.app.UiModeManager.MODE_NIGHT_NO
                    "dark" -> android.app.UiModeManager.MODE_NIGHT_YES
                    else -> android.app.UiModeManager.MODE_NIGHT_AUTO
                })
            }
            AppCompatDelegate.setDefaultNightMode(when (mode) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            })
        }
    }
}
