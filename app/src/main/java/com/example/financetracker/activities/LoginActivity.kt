package com.example.financetracker.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.databinding.ActivityLoginBinding
import com.example.financetracker.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NotificationHelper.createNotificationChannel(this)

        // Attach standard bottom navigation if present in layout
        com.example.financetracker.ui.BottomNavHelper.attach(this)

        val db = AppDatabase.getDatabase(this)
        val userDao = db.userProfileDao()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val loggedInUser = userDao.getLoggedInUser()
                if (loggedInUser != null) {
                    withContext(Dispatchers.Main) {
                        startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                        finish()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Auto-login failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.loginButton.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val user = userDao.login(email, password)
                    if (user != null) {
                        userDao.logoutAll()
                        user.isLoggedIn = true
                        userDao.update(user)

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@LoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()

                            NotificationHelper.sendNotification(
                                this@LoginActivity,
                                title = "Login Successful",
                                message = "Welcome back to FinMate!"
                            )

                            startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                            finish()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@LoginActivity, "Invalid email or password", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LoginActivity, "Login error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        binding.loginRedirectButton.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        binding.backArrow.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }
}
