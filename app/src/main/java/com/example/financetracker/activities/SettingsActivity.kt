package com.example.financetracker.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.utils.NotificationHelper
import com.example.financetracker.R
import com.example.financetracker.models.UserProfile
import com.example.financetracker.databinding.ActivitySettingsBinding
import com.example.financetracker.models.Budget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.content.SharedPreferences

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var user: UserProfile
    private var isPasswordVisible = false
    private val db by lazy { AppDatabase.getDatabase(this) }
    private val dao by lazy { db.userProfileDao() }
    private val budgetDao by lazy { db.budgetDao() }
    private val currencyOptions = listOf("LKR", "USD", "EUR", "INR", "GBP", "AUD")

    private lateinit var sharedPrefs: SharedPreferences
    private val PREFS_NAME = "finmate_settings"
    private val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Setup header (back button and title)
        val backArrow = findViewById<android.widget.ImageView>(com.example.financetracker.R.id.backArrow)
        val headerTitle = findViewById<android.widget.TextView>(com.example.financetracker.R.id.headerTitle)
        headerTitle?.text = "Settings"
        backArrow?.setOnClickListener { finish() }

        binding.exportImportButton.setOnClickListener {
            startActivity(Intent(this, ExportImportActivity::class.java))
        }

        // Setup notification switch using view binding
        binding.notificationSwitch.isChecked = isNotificationsEnabled()
        binding.notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            setNotificationsEnabled(isChecked)
            if (isChecked) {
                NotificationHelper.createNotificationChannel(this)
                Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show()
            }
        }

        NotificationHelper.createNotificationChannel(this)
        requestNotificationPermission()
        loadUserProfile()
        setupCurrencyDropdown()
        setupListeners()
        com.example.financetracker.ui.BottomNavHelper.attach(this)
    }

    private fun isNotificationsEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
    }

    private fun setNotificationsEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    /** Load user profile and initialize UI */
    /** Load user profile and initialize UI */
    /** Load user profile and initialize UI */
    private fun loadUserProfile() {
        lifecycleScope.launch {
            user = dao.getLoggedInUser() ?: return@launch
            binding.tvUserName.text = "Name: ${user.fullName}"
            binding.tvEmail.text = "Email: ${user.email}"
            binding.tvPassword.setText(user.password)
            binding.tvPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

            // Collect the Flow properly and get the first budget
            val savedCurrency = withContext(Dispatchers.IO) {
                val budgets = budgetDao.getAllBudgets().firstOrNull() // Collecting the flow
                budgets?.firstOrNull()?.currency?.takeIf { it in currencyOptions } ?: "LKR"
            }

            binding.currencyInput.setText(savedCurrency, false)
        }
    }



    /** Setup click listeners */
    private fun setupListeners() {
        binding.saveButton.setOnClickListener { saveUserProfile() }
        binding.logoutButton.setOnClickListener { showLogoutConfirmationDialog() }
        binding.resetButton.setOnClickListener { showPasswordConfirmationDialog() }
        binding.resetPasswordButton.setOnClickListener { showResetPasswordDialog() }
        binding.togglePasswordVisibility.setOnClickListener { togglePasswordVisibility() }
        // Notification switch handled in onCreate
    }

    /** Toggle password visibility */
    /** Toggle password visibility */
    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible

        // Toggle password input type
        binding.tvPassword.inputType = if (isPasswordVisible)
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        else
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        // Toggle password visibility icon
        binding.togglePasswordVisibility.setImageResource(
            if (isPasswordVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
        )

        // Ensure cursor stays at the end of the text
        binding.tvPassword.setSelection(binding.tvPassword.text.length)
    }


    /** Save updated user profile and currency */
    private fun saveUserProfile() {
        val updatedCurrency = binding.currencyInput.text.toString()
        val updatedPassword = binding.tvPassword.text.toString()

        if (updatedCurrency.isBlank() || updatedPassword.isBlank()) {
            Toast.makeText(this, "Currency or Password cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            // ✅ Save updated password
            user = user.copy(password = updatedPassword)
            dao.insertOrUpdate(user)

            // ✅ Save updated currency directly to database
            val budgets = budgetDao.getAllBudgets().firstOrNull()
            if (budgets.isNullOrEmpty()) {
                // If no budget exists, create a new one with the selected currency
                budgetDao.insertBudget(Budget(budgetKey = "default", amount = 0.0, currency = updatedCurrency))
            } else {
                // Update currency in all existing budgets
                budgets.forEach { budget ->
                    budgetDao.updateCurrency(budget.budgetKey, updatedCurrency)
                }
            }

            withContext(Dispatchers.Main) {
                // ✅ Show toast message for currency saved
                Toast.makeText(this@SettingsActivity, "Currency saved successfully!", Toast.LENGTH_SHORT).show()
                loadUserProfile()
            }
        }
    }



    /** Setup currency dropdown */
    /** Setup currency dropdown */
    private fun setupCurrencyDropdown() {
        val currencyAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, currencyOptions)
        binding.currencyInput.setAdapter(currencyAdapter)

        // Ensure dropdown is interactive
        binding.currencyInput.setOnClickListener {
            binding.currencyInput.showDropDown()
        }

        // Load the saved currency properly
        lifecycleScope.launch {
            val savedCurrency = withContext(Dispatchers.IO) {
                val budgets = budgetDao.getAllBudgets().firstOrNull()
                budgets?.firstOrNull()?.currency?.takeIf { it in currencyOptions } ?: "LKR"
            }
            binding.currencyInput.setText(savedCurrency, false)
        }
    }


    // Bottom navigation is handled centrally via BottomNavHelper

    /** Request notification permission */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }

    private fun showResetPasswordDialog() {
        val currentInput = EditText(this).apply {
            hint = "Current Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val newInput = EditText(this).apply {
            hint = "New Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(currentInput)
            addView(newInput)
            setPadding(50, 20, 50, 10)
        }

        AlertDialog.Builder(this)
            .setTitle("Reset Password")
            .setView(layout)
            .setPositiveButton("Reset") { _, _ ->
                if (currentInput.text.toString() == user.password) {
                    lifecycleScope.launch {
                        user = user.copy(password = newInput.text.toString())
                        dao.insertOrUpdate(user)
                        loadUserProfile()
                        Toast.makeText(this@SettingsActivity, "Password updated!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Incorrect current password", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPasswordConfirmationDialog() {
        val input = EditText(this).apply {
            hint = "Enter your password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle("Confirm Reset")
            .setView(input)
            .setPositiveButton("Confirm") { _, _ ->
                if (input.text.toString() == user.password) showFinalResetConfirmation()
                else Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                lifecycleScope.launch {
                    dao.logoutAll()
                    NotificationHelper.sendNotification(this@SettingsActivity, "Logout", "You are logged out.")
                    startActivity(Intent(this@SettingsActivity, LoginActivity::class.java))
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFinalResetConfirmation() {
        val input = EditText(this).apply {
            hint = "Type RESET to confirm"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(this)
            .setTitle("Final Confirmation")
            .setView(input)
            .setPositiveButton("Reset") { _, _ ->
                if (input.text.toString().equals("RESET", true)) {
                    lifecycleScope.launch {
                        dao.deleteAll()
                        Toast.makeText(this@SettingsActivity, "All data has been reset", Toast.LENGTH_SHORT).show()
                        NotificationHelper.sendNotification(this@SettingsActivity, "Reset", "All data deleted")
                        startActivity(Intent(this@SettingsActivity, LoginActivity::class.java))
                        finish()
                    }
                } else {
                    Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
