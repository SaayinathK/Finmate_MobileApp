package com.example.financetracker

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.financetracker.activities.PinActivity
import com.example.financetracker.activities.ReportsActivity
import com.example.financetracker.security.AppLock
import com.example.financetracker.security.PinStore
import com.google.android.material.textfield.TextInputEditText
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinLockTest {
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrument.targetContext
    private val store get() = PinStore(context)
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun await(check: () -> Boolean) {
        repeat(100) { if (check()) return; Thread.sleep(100) }
        assertTrue("Timed out waiting for app lock", check())
    }
    private fun pinActivity(): PinActivity? {
        var activity: PinActivity? = null
        val read = {
            activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<PinActivity>().firstOrNull()
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) read()
        else instrument.runOnMainSync(read)
        return activity
    }

    @Test fun encryptedVerifierSurvivesStoreRecreationAndPinReplacement() {
        assumeFalse("Do not overwrite a configured user PIN", store.enabled)
        try {
            store.save("0123".toCharArray(), "test@example.invalid")
            assertTrue(PinStore(context).enabled)
            assertTrue(PinStore(context).verify("0123".toCharArray()))
            assertFalse(store.verify("0124".toCharArray()))
            val prefs = context.getSharedPreferences("pin_security", 0)
            assertFalse(prefs.all.values.any { it == "0123" })
            val oldCipher = prefs.getString("ciphertext", null)
            store.save("9876".toCharArray(), "test@example.invalid")
            assertNotEquals(oldCipher, prefs.getString("ciphertext", null))
            assertFalse(store.verify("0123".toCharArray()))
            assertTrue(store.verify("9876".toCharArray()))
        } finally { store.disable() }
    }

    @Test fun pinAndRecoverySharePersistentCooldownIncludingReboot() {
        assumeFalse(store.enabled)
        try {
            store.save("0123".toCharArray(), "test@example.invalid")
            repeat(4) { assertFalse(store.verify("9999".toCharArray())) }
            assertFalse(store.verifyRecoveryPassword("wrong".toCharArray(), "account-password"))
            assertTrue(PinStore(context).remainingMillis() in 1..30_000)
            assertFalse(store.verify("0123".toCharArray()))
            assertFalse(store.verifyRecoveryPassword("account-password".toCharArray(), "account-password"))
            val prefs = context.getSharedPreferences("pin_security", 0)
            prefs.edit().putInt("boot", -123).putLong("until", 0L).commit()
            assertTrue(store.remainingMillis() in 29_000..30_000)
            prefs.edit().putLong("until", SystemClock.elapsedRealtime() - 1).commit()
            assertTrue(store.verifyRecoveryPassword("account-password".toCharArray(), "account-password"))
            assertEquals(0L, store.remainingMillis())
            assertEquals(0, prefs.getInt("failures", -1))
        } finally { store.disable() }
    }

    @Test fun protectedScreenStaysHiddenUntilUnlockAndRelocksAfterBackground() {
        assumeFalse(store.enabled)
        var scenario: ActivityScenario<Activity>? = null
        try {
            store.save("0123".toCharArray(), "test@example.invalid")
            instrument.runOnMainSync { AppLock.lock(context) }
            scenario = ActivityScenario.launch(Intent(context, ReportsActivity::class.java))
            await { pinActivity() != null }
            scenario.onActivity {
                assertEquals(View.INVISIBLE, it.window.decorView.visibility)
                assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
            fun enter(pin: String) {
                instrument.runOnMainSync {
                    val activity = requireNotNull(pinActivity())
                    val all = views(activity.findViewById(android.R.id.content))
                    all.filterIsInstance<TextInputEditText>().single().setText(pin)
                    all.filterIsInstance<TextView>().first { it.text.toString() == "Unlock" }.performClick()
                }
            }
            enter("9999")
            await { var wrong = false; instrument.runOnMainSync {
                pinActivity()?.let { wrong = views(it.findViewById(android.R.id.content)).filterIsInstance<TextView>().any { view -> view.text.toString().contains("Incorrect PIN") } }
            }; wrong }
            assertTrue(AppLock.isLocked(context))
            enter("0123")
            await { !AppLock.isLocked(context) }
            await { pinActivity() == null }
            scenario.onActivity { assertEquals(View.VISIBLE, it.window.decorView.visibility) }
            scenario.recreate()
            assertFalse("Rotation must preserve the foreground session", AppLock.isLocked(context))
            scenario.moveToState(Lifecycle.State.CREATED)
            await { AppLock.isLocked(context) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            await { pinActivity() != null }
        } finally {
            store.disable()
            instrument.runOnMainSync { pinActivity()?.finish() }
            scenario?.close()
        }
    }
}
