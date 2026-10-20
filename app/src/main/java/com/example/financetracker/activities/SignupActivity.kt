package com.example.financetracker.activities

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.R
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.UserProfile
import com.example.financetracker.ui.WalletScreen
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class SignupActivity : WalletScreen() {
    private lateinit var nameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private var submitting = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen("Create account", nav = false)
        content.addView(illustration(R.drawable.art_checklist, 96).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(96)) })

        val form = card()
        nameInput = field(form, "Your name", savedInstanceState?.getString("name") ?: "")
        emailInput = field(form, "Email", savedInstanceState?.getString("email") ?: "").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
        val password = field(form, "Password (min. 8 characters)").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        (password.parent.parent as? TextInputLayout)?.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        val confirm = field(form, "Confirm password").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val save = button("Create account", form) { }
        save.setOnClickListener {
            if (submitting) return@setOnClickListener
            val name = nameInput.text.toString().trim(); val email = emailInput.text.toString().trim()
            if (name.isBlank()) { nameInput.error = "Enter your name"; return@setOnClickListener }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { emailInput.error = "Enter a valid email"; return@setOnClickListener }
            if (password.text.toString().length < 8) { password.error = "Use at least 8 characters"; return@setOnClickListener }
            if (password.text.toString() != confirm.text.toString()) { confirm.error = "Passwords do not match"; return@setOnClickListener }
            submitting = true; save.isEnabled = false
            lifecycleScope.launch {
                try {
                    val dao = AppDatabase.getDatabase(this@SignupActivity).userProfileDao()
                    if (dao.getByEmail(email) != null) { emailInput.error = "This email already has an account. Sign in instead." }
                    else {
                        dao.logoutAll(); dao.insertOrUpdate(UserProfile(email, name, password.text.toString(), true))
                        com.example.financetracker.widget.TodayTasksWidget.refresh(this@SignupActivity)
                        val pending = intent.getStringExtra("pendingQuickAction")
                        if (pending in setOf("task", "loan", "transaction", "today")) {
                            startActivity(Intent(this@SignupActivity, QuickEntryActivity::class.java).setAction("com.example.financetracker.quick.$pending")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        } else startActivity(Intent(this@SignupActivity, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        finish()
                    }
                } catch (_: Exception) { toast("Could not create your account. Please try again.") }
                finally { submitting = false; save.isEnabled = true }
            }
        }
        button("Sign in", outlined = true) { finish() }
        content.addView(text("Saved on this device", 13, muted))
        animateEntrance(form)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (::nameInput.isInitialized) { outState.putString("name", nameInput.text.toString()); outState.putString("email", emailInput.text.toString()) }; super.onSaveInstanceState(outState)
    }
}
