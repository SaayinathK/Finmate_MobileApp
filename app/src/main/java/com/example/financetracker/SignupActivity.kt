package com.example.financetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.financetracker.databinding.ActivitySignupBinding

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle signup button click
        binding.signupButton.setOnClickListener {
            val fullName = binding.fullnameInput.text.toString().trim()
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (fullName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ✅ Save user data to SharedPreferences
            val sharedPref = getSharedPreferences("FinMateUserData", Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString("fullName", fullName)
                putString("email", email)
                putString("password", password)
                apply()
            }

            // Success message
            Toast.makeText(this, "Signup successful for $fullName", Toast.LENGTH_SHORT).show()

            // Navigate to login screen
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // Back arrow functionality
        binding.backArrow.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }
}
