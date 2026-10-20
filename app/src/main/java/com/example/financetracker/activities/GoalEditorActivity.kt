package com.example.financetracker.activities

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.FutureGoal
import com.example.financetracker.ui.AchievementCelebration
import com.example.financetracker.ui.WalletScreen
import com.example.financetracker.utils.PlannerRules
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.time.LocalDate

class GoalEditorActivity : WalletScreen() {
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var goal: FutureGoal? = null
    private var date = LocalDate.now().plusMonths(1)
    private lateinit var title: TextInputEditText
    private lateinit var notes: TextInputEditText
    private lateinit var target: TextInputEditText
    private lateinit var progress: TextInputEditText
    private lateinit var unit: TextInputEditText
    private var saving = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val id = savedInstanceState?.getInt("savedId", -1)?.takeIf { it > 0 } ?: intent.getIntExtra("goalId", -1)
            goal = db.goalDao().get(id)
            if (id != -1 && goal == null) { toast("This goal is no longer available"); finish(); return@launch }
            date = LocalDate.parse(savedInstanceState?.getString("date") ?: goal?.targetDate ?: date.toString())
            screen(if (goal == null) "New goal" else "Your goal", nav = false)
            val c = card()
            section("A clear target", "Track savings, learning, fitness or a personal milestone.", c)
            title = field(c, "Goal name", savedInstanceState?.getString("title") ?: goal?.title ?: "")
            notes = field(c, "Why it matters (optional)", savedInstanceState?.getString("notes") ?: goal?.notes ?: "", multiline = true)
            target = field(c, "Target (whole units)", savedInstanceState?.getString("target") ?: goal?.target?.toString() ?: "100", numeric = true)
            unit = field(c, "Unit (%, LKR, books, sessions…)", savedInstanceState?.getString("unit") ?: goal?.unit ?: "%")
            progress = field(c, "Progress so far (whole units)", savedInstanceState?.getString("progress") ?: goal?.progress?.toString() ?: "0", numeric = true)
            val day = button("Target date: ${PlannerRules.displayDate(date)}", c, true) {}
            day.setOnClickListener { pickDate(date) { date = it; day.text = "Target date: ${PlannerRules.displayDate(it)}" } }
            button("Save goal") { save() }
            if (goal != null) button("Delete goal", outlined = true) {
                confirmDeletion("Delete this goal?", "This removes the goal and its achievement.") { lifecycleScope.launch {
                    try { db.goalDao().delete(goal!!); finish() } catch (_: Exception) { toast("Could not delete goal") }
                } }
            }
            if (goal?.achievedAt != null) button("Celebrate achievement", outlined = true) { AchievementCelebration.show(this@GoalEditorActivity, goal!!.title) {} }
        }
    }
    private fun save() {
        if (saving) return
        val name = title.text.toString().trim()
        val total = target.text.toString().toLongOrNull()
        val current = progress.text.toString().toLongOrNull()
        if (name.isBlank()) { title.error = "Enter a goal name"; return }
        if (total == null || total !in 1..1_000_000_000_000L) { target.error = "Enter a whole number from 1 to 1 trillion"; return }
        if (current == null || current !in 0..total) { progress.error = "Enter a whole number from 0 to $total"; return }
        if (unit.text.toString().isBlank()) { unit.error = "Enter a unit"; return }
        saving = true
        lifecycleScope.launch {
            try {
                val reached = current == total
                val newlyAchieved = reached && goal?.achievedAt == null
                val updated = FutureGoal(id = goal?.id ?: 0, title = name, notes = notes.text.toString().trim(), targetDate = date.toString(), target = total, progress = current,
                    unit = unit.text.toString().trim(), achievedAt = if (reached) goal?.achievedAt ?: System.currentTimeMillis() else null)
                goal = if (goal == null) updated.copy(id = db.goalDao().insert(updated).toInt()) else { db.goalDao().update(updated); updated }
                if (newlyAchieved) AchievementCelebration.show(this@GoalEditorActivity, name) { finish() }
                else { toast("Goal saved"); finish() }
            } catch (_: Exception) { saving = false; toast("Could not save goal") }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (::title.isInitialized) {
            outState.putString("title", title.text.toString()); outState.putString("notes", notes.text.toString()); outState.putString("target", target.text.toString())
            outState.putString("progress", progress.text.toString()); outState.putString("unit", unit.text.toString()); outState.putString("date", date.toString())
            goal?.let { outState.putInt("savedId", it.id) }
        }
        super.onSaveInstanceState(outState)
    }
}
