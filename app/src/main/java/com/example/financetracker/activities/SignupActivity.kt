package com.example.financetracker.activities

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.databinding.ActivitySignupBinding
import com.example.financetracker.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Attach standard bottom navigation if present in layout
        com.example.financetracker.ui.BottomNavHelper.attach(this)

        val db = AppDatabase.getDatabase(this)
        val userDao = db.userProfileDao()

        binding.signupButton.setOnClickListener {
            val fullName = binding.fullnameInput.text.toString().trim()
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (fullName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newUser = UserProfile(
                email = email,
                fullName = fullName,
                password = password,
                isLoggedIn = false
            )

            CoroutineScope(Dispatchers.IO).launch {
                userDao.insertOrUpdate(newUser)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SignupActivity, "Signup successful!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@SignupActivity, LoginActivity::class.java))
                    finish()
                }
            }
        }

        binding.backArrow.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }
}
