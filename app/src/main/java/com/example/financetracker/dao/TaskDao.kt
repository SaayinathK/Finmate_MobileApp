package com.example.financetracker.dao

import androidx.room.*
import com.example.financetracker.models.DailyTask
import com.example.financetracker.models.TaskCompletion
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM daily_tasks ORDER BY reminderTime, id DESC")
    fun observeTasks(): Flow<List<DailyTask>>
    @Query("SELECT * FROM daily_tasks ORDER BY reminderTime, id DESC")
    suspend fun allTasks(): List<DailyTask>
    @Query("SELECT * FROM daily_tasks WHERE id = :id")
    suspend fun task(id: Int): DailyTask?
    @Query("SELECT * FROM daily_tasks WHERE parentTaskId = :id ORDER BY startDate, deadlineTime, id")
    suspend fun children(id: Int): List<DailyTask>
    @Insert suspend fun insert(task: DailyTask): Long
    @Update suspend fun update(task: DailyTask)
    @Query("SELECT * FROM task_completions ORDER BY completedAt DESC")
    fun observeCompletions(): Flow<List<TaskCompletion>>
    @Query("SELECT * FROM task_completions ORDER BY completedAt DESC")
    suspend fun completions(): List<TaskCompletion>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun complete(completion: TaskCompletion)
    @Query("DELETE FROM task_completions WHERE taskId = :taskId AND occurrenceDate = :date")
    suspend fun undo(taskId: Int, date: String)
    @Query("DELETE FROM daily_tasks WHERE id = :id")
    suspend fun deleteTask(id: Int)
    @Query("DELETE FROM task_completions WHERE taskId = :id")
    suspend fun deleteHistory(id: Int)
    @Query("DELETE FROM daily_tasks")
    suspend fun deleteAllTasks()
    @Query("DELETE FROM task_completions")
    suspend fun deleteAllHistory()
}
