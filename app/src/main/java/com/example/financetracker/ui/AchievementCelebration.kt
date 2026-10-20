package com.example.financetracker.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.FrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object AchievementCelebration {
    fun show(screen: WalletScreen, title: String, closed: () -> Unit) {
        val body = screen.column().apply { setPadding(screen.dp(24), screen.dp(8), screen.dp(24), screen.dp(16)); gravity = Gravity.CENTER_HORIZONTAL }
        val stage = FrameLayout(screen)
        stage.addView(WalletGuideView(screen).apply { gesture = WalletGuideView.Gesture.CELEBRATE },
            FrameLayout.LayoutParams(screen.dp(192), screen.dp(192), Gravity.CENTER))
        stage.addView(CelebrationView(screen), FrameLayout.LayoutParams(-1, -1))
        body.addView(stage, LinearLayout.LayoutParams(-1, screen.dp(204)))
        body.addView(screen.text(title, 22, screen.ink, true).apply { gravity = Gravity.CENTER })
        body.addView(screen.text("You reached your target. Keep growing.", 14, screen.muted).apply { gravity = Gravity.CENTER; setPadding(0, screen.dp(12), 0, 0) })
        val dialog = MaterialAlertDialogBuilder(screen).setTitle("Goal achieved!").setView(body).setPositiveButton("Continue", null).create()
        dialog.setOnDismissListener { closed() }; dialog.show()
    }
    private class CelebrationView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val colors = intArrayOf(Color.rgb(0, 170, 238), Color.rgb(22, 185, 126), Color.rgb(255, 193, 37), Color.rgb(153, 119, 244))
        private var fraction = 1f
        private var animator: ValueAnimator? = null
        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (WalletMotion.enabled()) animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1600; addUpdateListener { fraction = it.animatedValue as Float; invalidate() }; start()
            }
        }
        override fun onDetachedFromWindow() { animator?.cancel(); super.onDetachedFromWindow() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f; val cy = height / 2f
            if (fraction < 1f) repeat(40) { i ->
                val angle = i * 2 * PI / 40
                val distance = (0.3f + (i % 5) * 0.12f) * width * fraction
                val x = cx + cos(angle).toFloat() * distance
                val y = cy + sin(angle).toFloat() * distance + fraction * fraction * height * 0.35f
                paint.color = colors[i % colors.size]; paint.alpha = ((1f - fraction) * 255).toInt()
                canvas.save(); canvas.rotate(i * 29f + fraction * 240f, x, y)
                canvas.drawRoundRect(x - 5, y - 10, x + 5, y + 10, 2f, 2f, paint); canvas.restore()
            }
        }
    }
}
