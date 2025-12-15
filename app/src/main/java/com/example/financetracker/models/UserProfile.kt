package com.example.financetracker.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val email: String,
    val fullName: String,
    val password: String,
    var isLoggedIn: Boolean
)
