package com.example.financetracker.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "daily_tasks")
data class DailyTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val notes: String = "",
    val startDate: String,
    val recurrence: String = "ONCE",
    val weekdays: String = "",
    val reminderTime: String = "09:00",
    val reminderEnabled: Boolean = true,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "task_completions", indices = [Index(value = ["taskId", "occurrenceDate"], unique = true)])
data class TaskCompletion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int,
    val occurrenceDate: String,
    val completedAt: Long = System.currentTimeMillis(),
    val title: String,
    val notes: String
)
