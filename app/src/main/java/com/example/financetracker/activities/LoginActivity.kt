package com.example.financetracker.activities

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.R
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.ui.WalletScreen
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class LoginActivity : WalletScreen() {
    private lateinit var emailInput: TextInputEditText
    private var submitting = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch { if (db.userProfileDao().getLoggedInUser() != null) { goHome() } }
        screen("SWallet", nav = false)
        content.addView(illustration(R.drawable.art_wallet, 112).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(112)) })
        section("Sign in")
        val form = card()
        emailInput = field(form, "Email", savedInstanceState?.getString("email") ?: "").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            imeOptions = EditorInfo.IME_ACTION_NEXT
            setAutofillHints(android.view.View.AUTOFILL_HINT_EMAIL_ADDRESS)
        }
        val password = field(form, "Password").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE
            setAutofillHints(android.view.View.AUTOFILL_HINT_PASSWORD)
        }
        (password.parent.parent as? TextInputLayout)?.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        val login = button("Sign in", form) { }
        fun submit() {
            if (submitting) return
            val email = emailInput.text.toString().trim()
            if (email.isBlank()) { emailInput.error = "Enter your email"; return }
            if (password.text.isNullOrEmpty()) { password.error = "Enter your password"; return }
            submitting = true; login.isEnabled = false; login.text = "Signing in…"
            lifecycleScope.launch {
                try {
                    val user = db.userProfileDao().login(email, password.text.toString())
                    if (user == null) { password.error = "Email or password does not match" }
                    else {
                        db.userProfileDao().logoutAll(); db.userProfileDao().update(user.copy(isLoggedIn = true)); goHome()
                    }
                } catch (_: Exception) { toast("Could not sign in. Please try again.") }
                finally { submitting = false; login.isEnabled = true; login.text = "Sign in" }
            }
        }
        login.setOnClickListener { submit() }
        password.setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_DONE) { submit(); true } else false }
        button("Create account", outlined = true) { startActivity(Intent(this, SignupActivity::class.java).putExtra("pendingQuickAction", intent.getStringExtra("pendingQuickAction"))) }
        content.addView(text("Saved on this device", 13, muted))
        animateEntrance(form)
    }
    private fun goHome() {
        com.example.financetracker.widget.TodayTasksWidget.refresh(this)
        val pending = intent.getStringExtra("pendingQuickAction")
        if (pending in setOf("task", "loan", "transaction", "today")) {
            startActivity(Intent(this, QuickEntryActivity::class.java).setAction("com.example.financetracker.quick.$pending")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        } else startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
    override fun onSaveInstanceState(outState: Bundle) { if (::emailInput.isInitialized) outState.putString("email", emailInput.text.toString()); super.onSaveInstanceState(outState) }
}
