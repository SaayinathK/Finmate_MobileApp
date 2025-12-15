package com.example.financetracker.dao

import androidx.room.*
import com.example.financetracker.models.UserProfile

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profiles WHERE isLoggedIn = 1 LIMIT 1")
    suspend fun getLoggedInUser(): UserProfile?

    @Query("SELECT * FROM user_profiles WHERE email = :email AND password = :password LIMIT 1")
    suspend fun login(email: String, password: String): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(user: UserProfile)

    @Update
    suspend fun update(user: UserProfile)

    @Query("UPDATE user_profiles SET isLoggedIn = 0")
    suspend fun logoutAll()

    @Query("DELETE FROM user_profiles")
    suspend fun deleteAll()
}
