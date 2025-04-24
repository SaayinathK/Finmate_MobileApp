package com.example.financetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.financetracker.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ Retrieve stored credentials from SharedPreferences
        val prefs = getSharedPreferences("FinMateUserData", Context.MODE_PRIVATE)
        val registeredEmail = prefs.getString("email", null)
        val registeredPassword = prefs.getString("password", null)

        // Handle login button
        binding.signupButton.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (email == registeredEmail && password == registeredPassword) {
                // Save login status to SharedPreferences
                val editor = prefs.edit()
                editor.putBoolean("is_logged_in", true) // Set the user as logged in
                editor.apply()

                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show()
      ;       }
        }

        // Go to SignupActivity
        binding.loginRedirectButton.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
            finish()
        }

        // Handle back button
        binding.backArrow.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }
}
