package com.example.financetracker.dao

import androidx.room.*
import com.example.financetracker.models.FutureGoal

@Dao
interface GoalDao {
    @Query("SELECT * FROM future_goals ORDER BY achievedAt IS NOT NULL, targetDate, id DESC")
    suspend fun all(): List<FutureGoal>
    @Query("SELECT * FROM future_goals WHERE id = :id")
    suspend fun get(id: Int): FutureGoal?
    @Insert suspend fun insert(goal: FutureGoal): Long
    @Update suspend fun update(goal: FutureGoal)
    @Delete suspend fun delete(goal: FutureGoal)
}
