package com.example.financetracker.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "future_goals")
data class FutureGoal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val notes: String = "",
    val targetDate: String,
    val target: Long = 100,
    val progress: Long = 0,
    val unit: String = "%",
    val achievedAt: Long? = null
)
