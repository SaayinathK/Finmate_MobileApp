package com.example.financetracker.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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

/** A small, finite character animation. The shared atlas is decoded once, never per frame. */
class WalletGuideView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), DefaultLifecycleObserver {
    enum class Gesture(val row: Int, val restingFrame: Int, val sequence: List<Int>) {
        WELCOME(0, 0, listOf(0, 1, 3, 1)), PLAN(1, 3, listOf(0, 1, 2, 3)),
        ENCOURAGE(2, 0, listOf(0, 1, 2, 3)), CELEBRATE(3, 3, listOf(0, 3, 0, 3))
    }

    var gesture: Gesture = Gesture.WELCOME
        set(value) {
            if (field == value) return
            stopAnimation()
            field = value; frame = value.restingFrame; played = false
            invalidate(); playWhenVisible()
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val source = Rect()
    private val destination = RectF()
    private var atlas: Bitmap? = null
    private var character: AnimationCharacter? = null
    private var frame = 0
    private var played = false
    private var visibleToUser = false
    private var animator: ValueAnimator? = null
    private var owner: LifecycleOwner? = null
    internal val animationRunning get() = animator?.isRunning == true

    init {
        // The adjacent copy communicates the purpose; decorative motion is skipped by TalkBack.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isFocusable = false
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateCharacter()
        owner = findViewTreeLifecycleOwner()
        owner?.lifecycle?.addObserver(this)
        playWhenVisible()
    }
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        visibleToUser = isVisible
        if (isVisible) playWhenVisible() else stopAnimation()
    }
    override fun onStart(owner: LifecycleOwner) { updateCharacter(); playWhenVisible() }
    override fun onStop(owner: LifecycleOwner) { stopAnimation() }
    override fun onDetachedFromWindow() {
        stopAnimation(); owner?.lifecycle?.removeObserver(this); owner = null; atlas = null
        super.onDetachedFromWindow()
    }
    private fun playWhenVisible() {
        if (!isAttachedToWindow || !visibleToUser || played || isInEditMode) return
        if (owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == false) return
        if (!WalletMotion.enabled()) { frame = gesture.restingFrame; invalidate(); return }
        played = true
        animator = ValueAnimator.ofInt(0, 7).apply {
            duration = 1800L; interpolator = LinearInterpolator()
            addUpdateListener {
                if (!WalletMotion.enabled()) { stopAnimation(); return@addUpdateListener }
                frame = gesture.sequence[(it.animatedValue as Int) % 4]; invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { frame = gesture.restingFrame; invalidate() }
            })
            start()
        }
    }
    private fun updateCharacter() {
        val selected = AnimationCharacter.selected(context)
        if (character != selected || atlas == null) {
            stopAnimation(); played = false
            character = selected; atlas = sharedAtlas(context, selected.guideAtlas)
            invalidate()
        }
    }
    private fun stopAnimation() {
        animator?.cancel(); animator = null
        frame = gesture.restingFrame; invalidate()
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val defaultSize = (112 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(resolveSize(defaultSize + paddingLeft + paddingRight, widthMeasureSpec), resolveSize(defaultSize + paddingTop + paddingBottom, heightMeasureSpec))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = atlas ?: return
        // Proportional bounds also support atlases whose dimensions are not multiples of four.
        source.set(frame * bitmap.width / 4, gesture.row * bitmap.height / 4, (frame + 1) * bitmap.width / 4, (gesture.row + 1) * bitmap.height / 4)
        val cellWidth = source.width(); val cellHeight = source.height()
        val scale = minOf((width - paddingLeft - paddingRight).toFloat() / cellWidth, (height - paddingTop - paddingBottom).toFloat() / cellHeight)
        if (scale <= 0f) return
        val w = cellWidth * scale; val h = cellHeight * scale
        val left = paddingLeft + (width - paddingLeft - paddingRight - w) / 2f
        val top = paddingTop + (height - paddingTop - paddingBottom - h) / 2f
        destination.set(left, top, left + w, top + h)
        canvas.drawBitmap(bitmap, source, destination, paint)
    }
    companion object {
        private val cachedAtlases = mutableMapOf<Int, Bitmap>()
        private fun sharedAtlas(context: Context, resource: Int): Bitmap = synchronized(cachedAtlases) {
            cachedAtlases.getOrPut(resource) {
                requireNotNull(BitmapFactory.decodeResource(context.resources, resource, BitmapFactory.Options().apply { inScaled = false }))
            }
        }
    }
}
