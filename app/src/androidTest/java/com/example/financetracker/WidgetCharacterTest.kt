package com.example.financetracker

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Parcel
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.ViewFlipper
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.financetracker.activities.TaskEditorActivity
import com.example.financetracker.ui.AnimationCharacter
import com.example.financetracker.ui.WalletGuideView
import com.example.financetracker.widget.WidgetCharacter
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetCharacterTest {
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrument.targetContext
    private var previous: String? = null
    private val prefs get() = context.getSharedPreferences("finmate_settings", Context.MODE_PRIVATE)
    @Before fun rememberSelection() { previous = prefs.getString("animation_gender", null) }
    @After fun restoreSelection() { prefs.edit().putString("animation_gender", previous).commit() }

    // Exercise the same serialization and RemoteViews inflation used across the launcher boundary.
    private fun roundTrip(views: RemoteViews): RemoteViews {
        val parcel = Parcel.obtain()
        return try {
            views.writeToParcel(parcel, 0); parcel.setDataPosition(0)
            RemoteViews.CREATOR.createFromParcel(parcel)
        } finally { parcel.recycle() }
    }
    private fun await(check: () -> Boolean) {
        repeat(80) { if (check()) return; Thread.sleep(100) }
        assertTrue("Widget character did not become ready", check())
    }

    @Test fun reducedMotionUsesOneStillImageAndFollowsTheSelectedCharacter() {
        instrument.runOnMainSync {
            val frames = AnimationCharacter.entries.map { character ->
                AnimationCharacter.select(context, character)
                val views = roundTrip(WidgetCharacter.views(context, WalletGuideView.Gesture.WELCOME, motionEnabled = false))
                val image = views.apply(context, FrameLayout(context))
                assertTrue("Reduced motion must omit the flipping timer", image is ImageView)
                image.layout(0, 0, 128, 128)
                Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).also { image.draw(Canvas(it)) }
            }
            try {
                assertFalse("The widget must follow Male/Female selection", frames[0].sameAs(frames[1]))
                frames.forEach { bitmap ->
                    val pixels = IntArray(128 * 128); bitmap.getPixels(pixels, 0, 128, 0, 0, 128, 128)
                    assertTrue("The character must be visible", pixels.count { it ushr 24 > 32 } > 1000)
                }
            } finally { frames.forEach(Bitmap::recycle) }
        }
    }

    @Test fun launcherCompatibleFlipperAdvancesAndStopsAdvancingWhenDetached() {
        AnimationCharacter.select(context, AnimationCharacter.FEMALE)
        for (gesture in listOf(WalletGuideView.Gesture.WELCOME, WalletGuideView.Gesture.PLAN, WalletGuideView.Gesture.ENCOURAGE)) {
            val remote = roundTrip(WidgetCharacter.views(context, gesture, motionEnabled = true))
            ActivityScenario.launch<TaskEditorActivity>(Intent(context, TaskEditorActivity::class.java)).use { scenario ->
                await { var ready = false; scenario.onActivity { ready = it.findViewById<View>(R.id.taskName) != null }; ready }
                lateinit var flipper: ViewFlipper
                var first = -1
                scenario.onActivity { activity ->
                    val host = activity.findViewById<FrameLayout>(android.R.id.content)
                    flipper = remote.apply(activity, host) as ViewFlipper
                    host.addView(flipper, FrameLayout.LayoutParams(192, 192))
                    assertTrue(flipper.isAutoStart); assertEquals(9, flipper.childCount)
                    assertEquals(350, flipper.flipInterval)
                    repeat(flipper.childCount) { index ->
                        assertNotNull((flipper.getChildAt(index) as ImageView).drawable)
                    }
                    first = flipper.displayedChild
                }
                await { var advanced = false; scenario.onActivity { advanced = flipper.displayedChild != first }; advanced }
                var detachedFrame = -1
                scenario.onActivity {
                    (flipper.parent as ViewGroup).removeView(flipper)
                    detachedFrame = flipper.displayedChild
                }
                Thread.sleep(800)
                scenario.onActivity { assertEquals("Detached widgets must not keep flipping", detachedFrame, flipper.displayedChild) }
            }
        }
    }

    @Test fun compactWidgetKeepsSpaceForTasksAndTheAddButton() {
        ActivityScenario.launch<TaskEditorActivity>(Intent(context, TaskEditorActivity::class.java)).use { scenario ->
            await { var ready = false; scenario.onActivity { ready = it.findViewById<View>(R.id.taskName) != null }; ready }
            scenario.onActivity { activity ->
                val remote = RemoteViews(context.packageName, R.layout.widget_today_tasks).apply {
                    setTextViewText(R.id.widgetSubtitle, "Tue, 20 Oct · 2/3 done")
                    removeAllViews(R.id.widgetCharacter)
                    addView(R.id.widgetCharacter, WidgetCharacter.views(context, WalletGuideView.Gesture.PLAN, motionEnabled = false))
                }
                val parent = FrameLayout(activity)
                val widget = roundTrip(remote).apply(activity, parent)
                val density = activity.resources.displayMetrics.density
                fun dp(value: Int) = (value * density).toInt()
                widget.measure(View.MeasureSpec.makeMeasureSpec(dp(220), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dp(180), View.MeasureSpec.EXACTLY))
                widget.layout(0, 0, widget.measuredWidth, widget.measuredHeight)
                assertTrue("Task collection must remain usable at the minimum widget size", widget.findViewById<View>(R.id.widgetRows).height >= dp(40))
                assertEquals(dp(48), widget.findViewById<View>(R.id.widgetAdd).width)
                assertEquals(dp(64), widget.findViewById<View>(R.id.widgetCharacter).width)
            }
        }
    }
}
