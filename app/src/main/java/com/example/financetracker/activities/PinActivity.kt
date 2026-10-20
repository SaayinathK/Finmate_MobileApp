package com.example.financetracker.activities

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.R
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.security.AppLock
import com.example.financetracker.security.PinHasher
import com.example.financetracker.security.PinStore
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Secret fields and recovery authorization are deliberately never saved in instance state. */
class PinActivity : AppCompatActivity() {
    private val store by lazy { PinStore(this) }
    private var mode = UNLOCK
    private var busy = false
    private var recoveryVerified = false
    private var recoveryExpired = false
    private lateinit var form: LinearLayout
    private lateinit var status: TextView
    private lateinit var submit: MaterialButton
    private var current: TextInputEditText? = null
    private var next: TextInputEditText? = null
    private var confirmation: TextInputEditText? = null
    private var password: TextInputEditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (!busy) leave() }
        })
        mode = intent.getStringExtra(MODE)?.takeIf { it in listOf(UNLOCK, SETUP, CHANGE, DISABLE) } ?: UNLOCK
        if (mode == SETUP && store.enabled) mode = CHANGE
        if (mode != SETUP && !store.enabled) { finish(); return }
        render()
        lifecycleScope.launch {
            while (true) {
                val remaining = store.remainingMillis()
                if (!busy && ::submit.isInitialized) {
                    submit.isEnabled = remaining == 0L
                    if (remaining > 0) status.text = "Too many attempts. Try again in ${(remaining + 999) / 1000} seconds."
                    else if (status.text.startsWith("Too many attempts")) status.text = "You can try again."
                }
                delay(500)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (mode == UNLOCK && !AppLock.isLocked(this)) { finish(); return }
        if (recoveryExpired && ::form.isInitialized) {
            recoveryExpired = false; mode = RECOVER; render()
        }
    }

    override fun onStop() {
        if (recoveryVerified) { recoveryVerified = false; recoveryExpired = true }
        current?.text?.clear(); next?.text?.clear(); confirmation?.text?.clear(); password?.text?.clear()
        super.onStop()
    }

    private fun render() {
        current = null; next = null; confirmation = null; password = null
        val padding = (24 * resources.displayMetrics.density).toInt()
        val scroll = ScrollView(this).apply { isFillViewport = true; isSaveEnabled = false; fitsSystemWindows = true }
        form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(padding, padding, padding, padding); isSaveEnabled = false
        }
        scroll.addView(form); setContentView(scroll)
        form.addView(android.widget.ImageView(this).apply {
            setImageResource(R.drawable.swallet_logo); contentDescription = "SWallet"
            adjustViewBounds = true
        }, LinearLayout.LayoutParams(padding * 4, padding * 4).apply { gravity = Gravity.CENTER_HORIZONTAL })
        form.addView(TextView(this).apply {
            text = when (mode) { SETUP -> "Create your PIN"; CHANGE -> "Change PIN"; DISABLE -> "Remove PIN lock"; RECOVER -> "Recover your PIN"; RESET -> "Choose a new PIN"; else -> "Unlock SWallet" }
            textSize = 26f; gravity = Gravity.CENTER; setPadding(0, padding, 0, padding / 2)
        })
        form.addView(TextView(this).apply {
            text = when (mode) {
                SETUP -> "Use four digits to protect SWallet whenever you leave the app. Widget task details stay visible. Your account password can reset a forgotten PIN."
                RECOVER -> "Enter the password of the account that enabled PIN lock."
                RESET -> "Account verified. Enter and confirm a new four-digit PIN."
                DISABLE -> "Enter your current PIN to remove app lock."
                CHANGE -> "Enter your current PIN, then choose a new four-digit PIN."
                else -> "Enter your four-digit PIN to continue."
            }; textSize = 15f; gravity = Gravity.CENTER; setPadding(0, 0, 0, padding)
        })
        if (mode in listOf(UNLOCK, CHANGE, DISABLE)) current = secret("Current PIN")
        if (mode in listOf(SETUP, CHANGE, RESET)) { next = secret("New PIN"); confirmation = secret("Confirm new PIN") }
        if (mode == RECOVER) password = secret("Account password", numeric = false)
        status = TextView(this).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE; setPadding(0, padding / 2, 0, padding / 2) }
        form.addView(status)
        submit = MaterialButton(this).apply {
            text = when (mode) { UNLOCK -> "Unlock"; DISABLE -> "Remove PIN"; RECOVER -> "Verify account"; else -> "Save PIN" }
            setOnClickListener { perform() }
        }
        form.addView(submit)
        if (mode in listOf(UNLOCK, CHANGE, DISABLE)) form.addView(MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Forgot PIN?"; setOnClickListener { if (!busy) { mode = RECOVER; render() } }
        })
        form.addView(MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = if (mode == UNLOCK) "Close SWallet" else "Cancel"
            setOnClickListener { if (!busy) leave() }
        })
    }

    private fun secret(label: String, numeric: Boolean = true): TextInputEditText {
        val box = TextInputLayout(this).apply { hint = label; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE }
        val input = TextInputEditText(box.context).apply {
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            if (numeric) { keyListener = DigitsKeyListener.getInstance("0123456789"); filters = arrayOf(InputFilter.LengthFilter(4)) }
            transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
            isSaveEnabled = false; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            setAutofillHints(null)
            setSingleLine(true)
        }
        box.addView(input); form.addView(box)
        return input
    }

    private fun perform() {
        if (busy || store.remainingMillis() > 0) return
        val entered = current?.text?.toString()?.toCharArray() ?: charArrayOf()
        val newPin = next?.text?.toString()?.toCharArray() ?: charArrayOf()
        val confirm = confirmation?.text?.toString()?.toCharArray() ?: charArrayOf()
        val accountPassword = password?.text?.toString()?.toCharArray() ?: charArrayOf()
        if (current != null && !PinHasher.valid(entered)) {
            entered.fill('\u0000'); newPin.fill('\u0000'); confirm.fill('\u0000'); accountPassword.fill('\u0000')
            current?.error = "Enter four digits"; return
        }
        if (next != null && (!PinHasher.valid(newPin) || !newPin.contentEquals(confirm))) {
            entered.fill('\u0000'); newPin.fill('\u0000'); confirm.fill('\u0000'); accountPassword.fill('\u0000')
            next?.error = "Enter four digits and make both PINs match"; return
        }
        busy = true; submit.isEnabled = false
        form.touchables.forEach { it.isEnabled = false }
        val operation = mode
        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    when (operation) {
                        RECOVER -> {
                            val owner = store.ownerEmail?.let { AppDatabase.getDatabase(this@PinActivity).userProfileDao().getByEmail(it) }
                            store.verifyRecoveryPassword(accountPassword, owner?.password)
                        }
                        SETUP -> {
                            val owner = AppDatabase.getDatabase(this@PinActivity).userProfileDao().getLoggedInUser()
                                ?: error("Sign in before enabling PIN lock")
                            check(!store.enabled)
                            store.save(newPin, owner.email); true
                        }
                        RESET -> {
                            check(recoveryVerified) { "Verify your account again" }
                            store.save(newPin, requireNotNull(store.ownerEmail)); true
                        }
                        else -> {
                            if (!store.verify(entered)) false else {
                                if (operation == CHANGE) store.save(newPin, requireNotNull(store.ownerEmail))
                                if (operation == DISABLE) store.disable()
                                true
                            }
                        }
                    }
                }
                if (success && operation == RECOVER) {
                    // A result arriving in the background must not authorize a reset on return.
                    if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                        recoveryVerified = true; mode = RESET; render()
                    } else { recoveryVerified = false; recoveryExpired = true }
                } else if (success) {
                    recoveryVerified = false; recoveryExpired = false
                    if (AppLock.unlock(this@PinActivity)) finish()
                    else if (store.enabled) {
                        mode = UNLOCK; render()
                        status.text = "Unlock again when SWallet is in the foreground."
                    } else finish()
                } else status.text = if (operation == RECOVER) "Account password does not match." else "Incorrect PIN. Try again."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                status.text = "Could not verify or save. Try again, or use Forgot PIN to recover with your account password."
            } finally {
                entered.fill('\u0000'); newPin.fill('\u0000'); confirm.fill('\u0000'); accountPassword.fill('\u0000')
                current?.text?.clear(); next?.text?.clear(); confirmation?.text?.clear(); password?.text?.clear()
                busy = false; form.touchables.forEach { it.isEnabled = true }; submit.isEnabled = store.remainingMillis() == 0L
            }
        }
    }

    private fun leave() {
        if (mode == UNLOCK || (mode == RECOVER || mode == RESET) && AppLock.isLocked(this)) {
            if (mode != UNLOCK) { recoveryVerified = false; mode = UNLOCK; render() } else moveTaskToBack(true)
        } else finish()
    }
    companion object {
        const val MODE = "pinMode"
        const val UNLOCK = "unlock"
        const val SETUP = "setup"
        const val CHANGE = "change"
        const val DISABLE = "disable"
        private const val RECOVER = "recover"
        private const val RESET = "reset"
    }
}
