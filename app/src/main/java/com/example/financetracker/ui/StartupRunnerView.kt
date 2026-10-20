package com.example.financetracker.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.core.content.ContextCompat
import com.example.financetracker.R

/** Eight running poses plus forward travel, shown only during startup. */
class StartupRunnerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
    View(context, attrs, defStyle), DefaultLifecycleObserver {
    private var atlas: Bitmap? = null
    private var character: AnimationCharacter? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val source = Rect()
    private val target = RectF()
    private var phase = 0f
    private var played = false
    private var visibleToUser = false
    private var animator: ValueAnimator? = null
    private var owner: LifecycleOwner? = null
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateCharacter()
        owner = findViewTreeLifecycleOwner(); owner?.lifecycle?.addObserver(this)
        play()
    }
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible); visibleToUser = isVisible
        if (isVisible) play() else stop()
    }
    override fun onStart(owner: LifecycleOwner) { updateCharacter(); play() }
    override fun onStop(owner: LifecycleOwner) { stop() }
    override fun onDetachedFromWindow() {
        stop(); owner?.lifecycle?.removeObserver(this); owner = null; atlas = null
        super.onDetachedFromWindow()
    }
    private fun play() {
        if (!isAttachedToWindow || !visibleToUser || played || owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == false) return
        if (!WalletMotion.enabled()) { phase = 0.5f; invalidate(); return }
        played = true
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION; interpolator = LinearInterpolator()
            addUpdateListener {
                if (!WalletMotion.enabled()) { stop(); phase = 0.5f; invalidate(); return@addUpdateListener }
                phase = it.animatedValue as Float; invalidate()
            }
            start()
        }
    }
    private fun stop() { animator?.cancel(); animator = null }
    private fun updateCharacter() {
        val selected = AnimationCharacter.selected(context)
        if (character != selected || atlas == null) {
            stop(); played = false; phase = 0f
            character = selected
            atlas = BitmapFactory.decodeResource(resources, selected.runnerAtlas, BitmapFactory.Options().apply { inScaled = false })
            invalidate()
        }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        setMeasuredDimension(resolveSize((320 * density).toInt(), widthMeasureSpec), resolveSize((216 * density).toInt(), heightMeasureSpec))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = atlas ?: return
        val density = resources.displayMetrics.density
        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom - 12 * density
        val side = minOf(availableHeight, availableWidth * 0.64f)
        if (side <= 0f) return
        val frame = if (WalletMotion.enabled()) (phase * 24).toInt() % 8 else 0
        val column = frame % 4; val row = frame / 4
        source.set(column * bitmap.width / 4, row * bitmap.height / 2, (column + 1) * bitmap.width / 4, (row + 1) * bitmap.height / 2)
        val x = paddingLeft + (availableWidth - side) * phase
        val y = paddingTop + availableHeight - side
        target.set(x, y, x + side, y + side)
        paint.color = ContextCompat.getColor(context, R.color.startup_track)
        canvas.drawRoundRect(paddingLeft.toFloat(), paddingTop + availableHeight + 3 * density,
            (width - paddingRight).toFloat(), paddingTop + availableHeight + 5 * density, density, density, paint)
        paint.color = ContextCompat.getColor(context, R.color.startup_accent)
        val trailEnd = x + side * 0.58f
        canvas.drawRoundRect(maxOf(paddingLeft.toFloat(), trailEnd - 42 * density), paddingTop + availableHeight + 3 * density,
            trailEnd, paddingTop + availableHeight + 5 * density, density, density, paint)
        paint.color = android.graphics.Color.WHITE
        canvas.drawBitmap(bitmap, source, target, paint)
    }
    companion object { const val DURATION = 2400L }
}
