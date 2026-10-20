package com.example.financetracker

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.financetracker.activities.PinActivity
import com.example.financetracker.activities.ProfileActivity
import com.example.financetracker.activities.SplashActivity
import com.example.financetracker.security.AppLock
import com.example.financetracker.security.PinStore
import com.example.financetracker.ui.StartupRunnerView
import com.example.financetracker.ui.WalletMotion
import com.google.android.material.textfield.TextInputEditText
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupPlaybackTest {
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrument.targetContext
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun await(check: () -> Boolean) {
        repeat(100) { if (check()) return; Thread.sleep(100) }
        assertTrue("Startup playback did not become ready", check())
    }
    private fun animator(runner: StartupRunnerView): ValueAnimator? =
        StartupRunnerView::class.java.getDeclaredField("animator").apply { isAccessible = true }.get(runner) as? ValueAnimator
    private fun resumedPin(): PinActivity? = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<PinActivity>().firstOrNull()

    @Test fun waitingAtPinDoesNotConsumeTheVisibleStartupRun() {
        val store = PinStore(context)
        assumeFalse("Keep an existing user PIN intact", store.enabled)
        assumeTrue(WalletMotion.enabled())
        var scenario: ActivityScenario<SplashActivity>? = null
        try {
            store.save("0123".toCharArray(), "startup-test@example.invalid")
            instrument.runOnMainSync { AppLock.lock(context) }
            scenario = ActivityScenario.launch(Intent(context, SplashActivity::class.java))
            await { var ready = false; instrument.runOnMainSync { ready = resumedPin() != null }; ready }
            // Reproduce the regression: spend longer than the old startup timer on the PIN screen.
            Thread.sleep(StartupRunnerView.DURATION + 350)
            scenario.onActivity { assertEquals(View.INVISIBLE, it.window.decorView.visibility) }
            assertTrue(AppLock.isLocked(context))
            instrument.runOnMainSync {
                val pin = requireNotNull(resumedPin())
                val all = views(pin.findViewById(android.R.id.content))
                all.filterIsInstance<TextInputEditText>().single().setText("0123")
                all.filterIsInstance<TextView>().first { it.text.toString() == "Unlock" }.performClick()
            }
            var playback: ValueAnimator? = null
            val startup = scenario
            await {
                startup.onActivity { activity ->
                    if (activity.lifecycle.currentState == Lifecycle.State.RESUMED && activity.window.decorView.visibility == View.VISIBLE)
                        playback = animator(activity.findViewById(R.id.startupRunner))
                }
                playback?.isRunning == true
            }
            startup.onActivity {
                assertTrue("A fresh animation must remain after unlocking", requireNotNull(playback).currentPlayTime < StartupRunnerView.DURATION / 2)
                assertFalse(it.isFinishing)
                requireNotNull(playback).end()
            }
            await { startup.state == Lifecycle.State.DESTROYED }
        } finally {
            store.disable()
            instrument.runOnMainSync { resumedPin()?.finish() }
            scenario?.close()
        }
    }

    @Test fun restartingRunnerCancelsOldCompletionAndFinishesOnlyTheNewRun() {
        assumeFalse(PinStore(context).enabled)
        assumeTrue(WalletMotion.enabled())
        ActivityScenario.launch<ProfileActivity>(Intent(context, ProfileActivity::class.java)).use { scenario ->
            var runner: StartupRunnerView? = null
            scenario.onActivity { activity ->
                runner = StartupRunnerView(activity)
                activity.findViewById<FrameLayout>(android.R.id.content).addView(runner, FrameLayout.LayoutParams(320, 240))
            }
            await { var ready = false; scenario.onActivity { ready = animator(requireNotNull(runner)) != null }; ready }
            scenario.onActivity { activity ->
                val view = requireNotNull(runner)
                var cancelledCalls = 0
                var currentCalls = 0
                view.restart { cancelledCalls++ }
                view.cancelPlayback()
                assertEquals(0, cancelledCalls)
                view.restart { currentCalls++ }
                val running = requireNotNull(animator(view))
                running.end()
                assertEquals(0, cancelledCalls)
                assertEquals(1, currentCalls)
                running.end()
                assertEquals(1, currentCalls)
                activity.findViewById<FrameLayout>(android.R.id.content).removeView(view)
            }
        }
    }
}
