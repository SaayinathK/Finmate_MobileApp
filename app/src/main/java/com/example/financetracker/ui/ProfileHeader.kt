package com.example.financetracker.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.UserProfile
import kotlinx.coroutines.launch
import java.util.Locale

/** Shared identity and activity section for the merged Profile and Settings destination. */
suspend fun WalletScreen.profileHeader(db: AppDatabase, user: UserProfile?, updated: () -> Unit) {
    val prefs = getSharedPreferences("finmate_settings", Context.MODE_PRIVATE)
    val headlineKey = "profile_headline_${user?.email.orEmpty()}"
    val headline = prefs.getString(headlineKey, "Personal account").orEmpty()
    val hero = card(color = softBlue).apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(20), dp(20), dp(20), dp(20)) }
    val initials = (user?.fullName ?: "SWallet").trim().split(Regex("\\s+")).take(2).joinToString("") { it.take(1) }.uppercase(Locale.getDefault())
    hero.addView(text(initials, 28, Color.WHITE, true).apply {
        gravity = Gravity.CENTER; background = shape(actionBlue, 40); contentDescription = "Profile initials $initials"
    }, LinearLayout.LayoutParams(dp(80), dp(80)).apply { bottomMargin = dp(12) })
    hero.addView(text(user?.fullName ?: "SWallet member", 25, ink, true).apply { gravity = Gravity.CENTER })
    hero.addView(text(headline, 14, blue).apply { gravity = Gravity.CENTER })
    hero.addView(text(user?.email ?: "Saved on this device", 13, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(8)) })
    if (user != null) button("Edit profile", hero, true) {
        val form = column().apply { setPadding(dp(24), dp(8), dp(24), dp(8)) }
        val name = field(form, "Full name", user.fullName)
        val bio = field(form, "Headline", headline)
        val dialog = AlertDialog.Builder(this).setTitle("Edit profile").setView(form).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = name.text.toString().trim()
            if (value.isBlank()) { name.error = "Enter your name"; return@setOnClickListener }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            lifecycleScope.launch {
                try {
                    db.userProfileDao().update(user.copy(fullName = value))
                    prefs.edit().putString(headlineKey, bio.text.toString().trim().ifBlank { "Personal account" }).apply()
                    dialog.dismiss(); updated()
                } catch (_: Exception) { dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true; toast("Could not save profile") }
            }
        } }; dialog.show()
    }
    val values = listOf("Transactions" to db.transactionDao().getAllTransactionsRaw().size,
        "Tasks done" to db.taskDao().completions().size, "Achievements" to db.goalDao().all().count { it.achievedAt != null })
    val stats = row()
    values.forEach { (label, value) -> card(stats).apply {
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), dp(8)) }
        addView(text(value.toString(), 24, blue, true)); addView(text(label, 11, muted).apply { gravity = Gravity.CENTER })
    } }
    content.addView(stats)
}
