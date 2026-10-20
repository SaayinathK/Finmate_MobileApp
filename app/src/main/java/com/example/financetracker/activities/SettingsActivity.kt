package com.example.financetracker.activities

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.receiver.NotificationScheduler
import com.example.financetracker.ui.WalletScreen
import com.example.financetracker.ui.profileHeader
import com.example.financetracker.R
import com.example.financetracker.ui.AnimationCharacter
import com.example.financetracker.ui.WalletGuideView
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first

open class SettingsActivity : WalletScreen() {
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var renderJob: Job? = null
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }
    override fun onResume() { super.onResume(); render() }
    private fun render() {
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            val user = db.userProfileDao().getLoggedInUser()
            val prefs = getSharedPreferences("finmate_settings", MODE_PRIVATE)
            screen("Profile", "Account, tools & settings")
            profileHeader(db, user) { render() }
            section("Tools")
            val tools = card()
            button("Reports", tools, true) { open(ReportsActivity::class.java) }
            button("Loans", tools, true) { open(LoansActivity::class.java) }
            button("Goals & achievements", tools, true) { open(GoalsActivity::class.java) }
            button("Reminders", tools, true) { open(ReminderSettingsActivity::class.java) }
            button("Excel", tools, true) { open(ExportImportActivity::class.java) }
            section("Preferences")
            val c = card()
            val character = AnimationCharacter.selected(this@SettingsActivity)
            val characterName = getString(character.label)
            val animationPreview = row()
            animationPreview.addView(guide(WalletGuideView.Gesture.WELCOME, 96))
            animationPreview.addView(column().apply {
                addView(text(getString(R.string.animation_gender), 16, ink, true))
                addView(text(getString(R.string.animation_character_summary), 13, muted))
            }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            c.addView(animationPreview)
            button(getString(R.string.animation_character_current, characterName), c, true) {
                val characters = AnimationCharacter.entries
                AlertDialog.Builder(this@SettingsActivity).setTitle(R.string.animation_gender)
                    .setSingleChoiceItems(characters.map { getString(it.label) }.toTypedArray(), characters.indexOf(character)) { dialog, which ->
                        AnimationCharacter.select(this@SettingsActivity, characters[which])
                        com.example.financetracker.widget.TodayTasksWidget.refresh(this@SettingsActivity)
                        dialog.dismiss(); render()
                    }.setNegativeButton("Cancel", null).show()
            }
            val currency = prefs.getString("currency", null) ?: db.budgetDao().getAllBudgets().first().firstOrNull()?.currency ?: "LKR"
            button("Currency: $currency", c, true) {
                val options = arrayOf("LKR", "USD", "EUR", "INR", "GBP", "AUD")
                AlertDialog.Builder(this@SettingsActivity).setTitle("Display currency").setSingleChoiceItems(options, options.indexOf(currency)) { dialog, which ->
                    prefs.edit().putString("currency", options[which]).apply()
                    lifecycleScope.launch { db.budgetDao().getAllBudgets().first().forEach { db.budgetDao().updateCurrency(it.budgetKey, options[which]) }; render() }
                    dialog.dismiss()
                }.setNegativeButton("Cancel", null).show()
            }
            c.addView(text("Display only; no currency conversion.", 13, muted))
            val themes = listOf("system", "light", "dark")
            val themeNames = arrayOf("System", "Light", "Dark")
            val selectedTheme = themes.indexOf(prefs.getString("theme", "system")).coerceAtLeast(0)
            button("Theme: ${themeNames[selectedTheme]}", c, true) {
                AlertDialog.Builder(this@SettingsActivity).setTitle("Appearance")
                    .setSingleChoiceItems(themeNames, selectedTheme) { dialog, which ->
                        prefs.edit().putString("theme", themes[which]).apply(); dialog.dismiss()
                        com.example.financetracker.WalletApplication.applyTheme(this@SettingsActivity)
                        com.example.financetracker.widget.TodayTasksWidget.refresh(this@SettingsActivity)
                        render()
                    }.setNegativeButton("Cancel", null).show()
            }
            button("Today's tasks widget", c, true) {
                val manager = android.appwidget.AppWidgetManager.getInstance(this@SettingsActivity)
                if (manager.isRequestPinAppWidgetSupported) manager.requestPinAppWidget(
                    android.content.ComponentName(this@SettingsActivity, com.example.financetracker.widget.TodayTasksWidget::class.java), null, null)
                else toast("Long-press your home screen, open Widgets, then choose SWallet")
            }
            button("Monthly budget", c, true) { open(BudgetSetupActivity::class.java) }
            section("Delete data")
            val deletion = card()
            button("Delete all transactions", deletion, true) {
                confirmDeletion("Delete all transactions?", "All income and expense history and every linked loan will be removed, including shared bills and payments. Unlinked older loans, tasks and budgets will stay. Export a copy first if needed.") {
                    lifecycleScope.launch {
                        try { com.example.financetracker.repository.DataMaintenance.deleteTransactions(db); toast("All transactions deleted") }
                        catch (_: Exception) { toast("Could not delete transactions") }
                    }
                }
            }.setTextColor(red)
            button("Delete all tasks", deletion, true) {
                confirmDeletion("Delete all tasks?", "All tasks, completed history and task reminders will be removed.") {
                    lifecycleScope.launch {
                        try {
                            com.example.financetracker.repository.DataMaintenance.deleteTasks(db)
                            NotificationScheduler.rescheduleTasks(this@SettingsActivity); toast("All tasks deleted")
                        } catch (_: Exception) { toast("Could not delete tasks") }
                    }
                }
            }.setTextColor(red)
            if (user != null) {
                button("Change password", outlined = true) {
                    val form = column().apply { setPadding(dp(24), dp(8), dp(24), dp(8)) }
                    val old = field(form, "Current password").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
                    val next = field(form, "New password (min. 8 characters)").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
                    val dialog = AlertDialog.Builder(this@SettingsActivity).setTitle("Change password").setView(form).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
                    dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (old.text.toString() != user.password) old.error = "Current password does not match"
                        else if (next.text.toString().length < 8) next.error = "Use at least 8 characters"
                        else lifecycleScope.launch { db.userProfileDao().insertOrUpdate(user.copy(password = next.text.toString())); dialog.dismiss(); toast("Password updated") }
                    } }; dialog.show()
                }
                button("Log out", outlined = true) {
                    AlertDialog.Builder(this@SettingsActivity).setTitle("Log out?").setMessage("Your accounts and tasks will remain on this device.")
                        .setPositiveButton("Log out") { _, _ -> lifecycleScope.launch { db.userProfileDao().logoutAll(); com.example.financetracker.widget.TodayTasksWidget.refresh(this@SettingsActivity); startActivity(Intent(this@SettingsActivity, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish() } }.setNegativeButton("Cancel", null).show()
                }
            }
        }
    }
}
