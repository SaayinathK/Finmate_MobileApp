package com.example.financetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.financetracker.databinding.ActivityLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var googleSignInClient: GoogleSignInClient

    private val RC_SIGN_IN = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NotificationHelper.createNotificationChannel(this)

        val prefs = getSharedPreferences("FinMateUserData", Context.MODE_PRIVATE)
        val registeredEmail = prefs.getString("email", null)
        val registeredPassword = prefs.getString("password", null)

        // ✅ Google Sign-In options
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Handle Google Sign-In Button
        binding.googleSignInButton.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        // Handle normal email/password login
        binding.signupButton.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (email == registeredEmail && password == registeredPassword) {
                val editor = prefs.edit()
                editor.putBoolean("is_logged_in", true)
                editor.apply()

                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()

                NotificationHelper.sendNotification(
                    this,
                    title = "Login Successful",
                    message = "Welcome back to FinMate!"
                )

                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show()
            }
        }

        binding.loginRedirectButton.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
            finish()
        }

        binding.backArrow.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    // ✅ Handle Google Sign-In Result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)

                // Sign in success
                Toast.makeText(this, "Google Sign-In Successful: ${account.email}", Toast.LENGTH_SHORT).show()

                val prefs = getSharedPreferences("FinMateUserData", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                editor.putBoolean("is_logged_in", true)
                editor.apply()

                NotificationHelper.sendNotification(
                    this,
                    title = "Google Login Successful",
                    message = "Welcome back to FinMate!"
                )

                startActivity(Intent(this, HomeActivity::class.java))
                finish()

            } catch (e: ApiException) {
                Log.e("Google Sign-In", "SignIn Failed", e)
                Toast.makeText(this, "Google Sign-In Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
