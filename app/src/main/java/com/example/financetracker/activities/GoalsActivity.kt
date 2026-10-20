package com.example.financetracker.activities

import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.R
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.ui.WalletScreen
import com.example.financetracker.ui.WalletMotion
import com.example.financetracker.ui.WalletGuideView
import com.example.financetracker.utils.PlannerRules
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate

class GoalsActivity : WalletScreen() {
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var achievements = false
    private var renderJob: Job? = null
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); achievements = savedInstanceState?.getBoolean("achievements") ?: false }
    override fun onResume() { super.onResume(); render() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("achievements", achievements); super.onSaveInstanceState(outState) }
    private fun render() {
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            val goals = db.goalDao().all()
            screen("Goals & achievements", "Build toward what matters to you", nav = false, floatingAction = "Add goal" to { open(GoalEditorActivity::class.java) })
            val overview = card(color = softBlue)
            val summary = row()
            summary.addView(column().apply {
                addView(text("${goals.count { it.achievedAt == null }} goals in progress", 17, blue, true))
                addView(text("${goals.count { it.achievedAt != null }} achievements", 14, muted))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            summary.addView(guide(if (achievements && goals.any { it.achievedAt != null }) WalletGuideView.Gesture.CELEBRATE else WalletGuideView.Gesture.PLAN, 112))
            overview.addView(summary)
            choices(content, listOf("Future goals", "Achievements"), if (achievements) 1 else 0) { achievements = it == 1; render() }
            val visible = goals.filter { (it.achievedAt != null) == achievements }
            if (visible.isEmpty()) emptyState(R.drawable.swallet_logo, if (achievements) "Your next milestone awaits" else "Give your future a goal", if (achievements) "Completed goals appear here." else "Set a target, a date and track your progress.")
            visible.forEach { goal ->
                val c = card()
                val percent = (goal.progress.toDouble() / goal.target * 100).toInt().coerceIn(0, 100)
                c.addView(text(if (achievements) "★  ${goal.title}" else goal.title, 19, if (achievements) green else ink, true))
                if (goal.notes.isNotBlank()) c.addView(text(goal.notes, 13, muted))
                val date = LocalDate.parse(goal.targetDate)
                val overdue = !achievements && date < LocalDate.now()
                c.addView(text("Target: ${PlannerRules.displayDate(date)}${if (overdue) " · Overdue" else ""}", 12, if (overdue) red else muted))
                c.addView(ProgressBar(this@GoalsActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100; progressTintList = android.content.res.ColorStateList.valueOf(if (achievements) green else blue)
                    setProgress(percent, WalletMotion.enabled()); contentDescription = "$percent percent complete"
                }, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(12); bottomMargin = dp(8) })
                c.addView(text("${goal.progress} / ${goal.target} ${goal.unit} · $percent%", 14, if (achievements) green else blue, true))
                if (goal.achievedAt != null) c.addView(text("Achieved ${PlannerRules.displayDate(java.time.Instant.ofEpochMilli(goal.achievedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate())}", 12, muted))
                button(if (achievements) "View achievement" else "Update progress", c, true) {
                    startActivity(Intent(this@GoalsActivity, GoalEditorActivity::class.java).putExtra("goalId", goal.id))
                }
            }
        }
    }
}
