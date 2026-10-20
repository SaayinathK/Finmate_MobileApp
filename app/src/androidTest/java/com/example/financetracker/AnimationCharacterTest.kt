package com.example.financetracker

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.financetracker.activities.ProfileActivity
import com.example.financetracker.ui.AnimationCharacter
import com.example.financetracker.ui.StartupRunnerView
import com.example.financetracker.ui.WalletGuideView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnimationCharacterTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("finmate_settings", Context.MODE_PRIVATE)
    private var previous: String? = null

    @Before fun rememberSelection() {
        previous = prefs.getString("animation_gender", null)
        prefs.edit().remove("animation_gender").commit()
    }
    @After fun restoreSelection() { prefs.edit().putString("animation_gender", previous).commit() }

    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun await(check: () -> Boolean) {
        repeat(100) { if (check()) return; Thread.sleep(100) }
        assertTrue("Character UI did not become ready", check())
    }
    private fun awaitLabel(scenario: ActivityScenario<ProfileActivity>, label: String) {
        await {
            var ready = false
            scenario.onActivity { activity -> ready = views(activity.findViewById(android.R.id.content)).filterIsInstance<Button>().any { it.text.toString() == label } }
            ready
        }
    }
    private fun openSelection(scenario: ActivityScenario<ProfileActivity>) {
        scenario.onActivity { activity ->
            views(activity.findViewById(android.R.id.content)).filterIsInstance<Button>().first { it.text.toString().startsWith("Animation gender:") }.performClick()
        }
    }

    @Test fun profileDefaultsToMalePersistsFemaleAndAllowsSwitchingBack() {
        assertEquals(AnimationCharacter.MALE, AnimationCharacter.selected(context))
        ActivityScenario.launch<ProfileActivity>(Intent(context, ProfileActivity::class.java)).use { scenario ->
            awaitLabel(scenario, "Animation gender: Male")
            openSelection(scenario); onView(withText("Cancel")).perform(click())
            assertFalse(prefs.contains("animation_gender"))
            openSelection(scenario); onView(withText("Female")).perform(click())
            awaitLabel(scenario, "Animation gender: Female")
            assertEquals("female", prefs.getString("animation_gender", null))
            scenario.recreate(); awaitLabel(scenario, "Animation gender: Female")
            assertEquals(AnimationCharacter.FEMALE, AnimationCharacter.selected(context))
            openSelection(scenario); onView(withText("Male")).perform(click())
            awaitLabel(scenario, "Animation gender: Male")
            scenario.recreate(); awaitLabel(scenario, "Animation gender: Male")
        }
        prefs.edit().putString("animation_gender", "unknown-old-value").commit()
        assertEquals(AnimationCharacter.MALE, AnimationCharacter.selected(context))
    }

    @Test fun guideAndRunnerRefreshTheirArtworkAndDoNotReuseTheWrongCachedAtlas() {
        ActivityScenario.launch<ProfileActivity>(Intent(context, ProfileActivity::class.java)).use { scenario ->
            awaitLabel(scenario, "Animation gender: Male")
            scenario.onActivity { activity ->
                val host = activity.findViewById<FrameLayout>(android.R.id.content)
                val guide = WalletGuideView(activity)
                val runner = StartupRunnerView(activity)
                host.addView(guide, FrameLayout.LayoutParams(256, 256))
                host.addView(runner, FrameLayout.LayoutParams(256, 256))
                fun snapshots(): List<Bitmap> {
                    guide.onStart(activity); guide.onStop(activity)
                    runner.onStart(activity); runner.onStop(activity)
                    return listOf(guide, runner).map { view ->
                        view.layout(0, 0, 256, 256)
                        Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
                    }
                }
                val male = snapshots()
                AnimationCharacter.select(activity, AnimationCharacter.FEMALE)
                val female = snapshots()
                AnimationCharacter.select(activity, AnimationCharacter.MALE)
                val restored = snapshots()
                try {
                    male.indices.forEach { index ->
                        val pixels = IntArray(256 * 256)
                        female[index].getPixels(pixels, 0, 256, 0, 0, 256, 256)
                        assertTrue("Female artwork must be visible", pixels.count { it ushr 24 > 32 } > 1000)
                        assertFalse("Male and female must render different artwork", male[index].sameAs(female[index]))
                        assertTrue("Switching back must restore male artwork", male[index].sameAs(restored[index]))
                    }
                } finally {
                    (male + female + restored).forEach(Bitmap::recycle)
                    host.removeView(guide); host.removeView(runner)
                }
            }
        }
    }
}
