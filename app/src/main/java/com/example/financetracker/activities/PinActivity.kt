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

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun color(id: Int) = androidx.core.content.ContextCompat.getColor(this, id)
    private fun rounded(fill: Int, radius: Int, stroke: Int? = null) = android.graphics.drawable.GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }
    private fun label(value: String, size: Float, tint: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(tint); gravity = Gravity.CENTER
        typeface = androidx.core.content.res.ResourcesCompat.getFont(this@PinActivity,
            if (bold) R.font.inter_semibold else R.font.inter_medium)
    }
    private fun render() {
        current = null; next = null; confirmation = null; password = null
        val background = color(R.color.startup_background)
        val ink = color(R.color.startup_ink)
        val muted = color(R.color.startup_muted)
        val accent = color(R.color.blue_700)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            val light = resources.getBoolean(R.bool.wallet_light_bars)
            isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light
        }
        window.statusBarColor = background; window.navigationBarColor = background
        val scroll = ScrollView(this).apply {
            isFillViewport = true; isSaveEnabled = false; setBackgroundColor(background)
            clipToPadding = false
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(24), dp(28), dp(24), dp(24)); isSaveEnabled = false
        }
        scroll.addView(page); setContentView(scroll)
        page.addView(label("S W A L L E T", 12f, accent, true))
        page.addView(android.widget.ImageView(this).apply {
            setImageResource(R.drawable.swallet_launcher_symbol); contentDescription = "SWallet"
            this.background = rounded(color(R.color.wallet_soft_blue), 32)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }, LinearLayout.LayoutParams(dp(108), dp(108)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(20); bottomMargin = dp(22) })
        val title = when (mode) { SETUP -> "Create your PIN"; CHANGE -> "Change PIN"; DISABLE -> "Remove PIN lock"; RECOVER -> "Recover your PIN"; RESET -> "Choose a new PIN"; else -> "Unlock SWallet" }
        page.addView(label(title, 28f, ink, true).apply { accessibilityHeadingCompat() })
        page.addView(label(when (mode) {
            SETUP -> "Four digits. A little more peace of mind."
            RECOVER -> "Verify your account to choose a new PIN."
            RESET -> "Account verified. Make your new PIN yours."
            DISABLE -> "Confirm your PIN to turn off app lock."
            CHANGE -> "Confirm your PIN, then choose four new digits."
            else -> "Welcome back. Your wallet is ready."
        }, 14f, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(26) })
        form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(16))
            this.background = rounded(color(R.color.unlock_surface), 26, color(R.color.wallet_border)); isSaveEnabled = false
        }
        page.addView(form, LinearLayout.LayoutParams(-1, -2).apply {
            // Keep the form readable on tablets and landscape screens.
            val available = resources.displayMetrics.widthPixels - dp(48)
            width = minOf(available, dp(420)); gravity = Gravity.CENTER_HORIZONTAL
        })
        val securityLabel = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        securityLabel.addView(android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_lock_outline); setColorFilter(accent); contentDescription = null
        }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) })
        securityLabel.addView(label(if (mode == RECOVER) "ACCOUNT VERIFICATION" else "PRIVATE ACCESS", 10f, accent, true))
        form.addView(securityLabel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })
        if (mode in listOf(UNLOCK, CHANGE, DISABLE)) current = secret("Current PIN")
        if (mode in listOf(SETUP, CHANGE, RESET)) { next = secret("New PIN"); confirmation = secret("Confirm new PIN") }
        if (mode == RECOVER) password = secret("Account password", numeric = false)
        status = label("", 12f, color(R.color.red)).apply {
            gravity = Gravity.START; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setPadding(0, dp(8), 0, dp(8))
        }
        form.addView(status)
        submit = MaterialButton(this).apply {
            text = when (mode) { UNLOCK -> "Unlock"; DISABLE -> "Remove PIN"; RECOVER -> "Verify account"; else -> "Save PIN" }
            isAllCaps = false; textSize = 16f; minHeight = dp(56); cornerRadius = dp(16)
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.rgb(0, 105, 224))
            setTextColor(android.graphics.Color.WHITE)
            icon = androidx.core.content.ContextCompat.getDrawable(this@PinActivity, R.drawable.ic_lock_outline)
            iconTint = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            setOnClickListener { perform() }
        }
        form.addView(submit, LinearLayout.LayoutParams(-1, dp(60)))
        if (mode in listOf(UNLOCK, CHANGE, DISABLE)) form.addView(MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Forgot PIN?"; isAllCaps = false; setTextColor(accent)
            setOnClickListener { if (!busy) { mode = RECOVER; render() } }
        }, LinearLayout.LayoutParams(-1, dp(48)))
        form.addView(MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = if (mode == UNLOCK) "Close SWallet" else "Cancel"
            isAllCaps = false; setTextColor(muted)
            setOnClickListener { if (!busy) leave() }
        }, LinearLayout.LayoutParams(-1, dp(48)))
        page.addView(label(if (mode == SETUP) "Your account password can reset a forgotten PIN.\nWidget task details stay visible." else "Your money. Your plans. Your space.", 12f, muted),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
    }
    private fun View.accessibilityHeadingCompat() { androidx.core.view.ViewCompat.setAccessibilityHeading(this, true) }

    private fun secret(label: String, numeric: Boolean = true): TextInputEditText {
        val box = TextInputLayout(this).apply {
            hint = label; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxBackgroundColor = color(R.color.startup_background); boxStrokeColor = color(R.color.blue_700)
            defaultHintTextColor = android.content.res.ColorStateList.valueOf(color(R.color.startup_muted))
            setBoxCornerRadii(dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat())
        }
        val input = TextInputEditText(box.context).apply {
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            if (numeric) { keyListener = DigitsKeyListener.getInstance("0123456789"); filters = arrayOf(InputFilter.LengthFilter(4)) }
            transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
            textSize = if (numeric) 26f else 16f
            if (numeric) { gravity = Gravity.CENTER; letterSpacing = 0.35f }
            setTextColor(color(R.color.startup_ink)); minHeight = dp(64)
            setPadding(dp(16), dp(16), dp(16), dp(12))
            isSaveEnabled = false; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            setAutofillHints(null); setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, action, _ ->
                if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { perform(); true } else false
            }
        }
        box.addView(input); form.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(4) })
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
