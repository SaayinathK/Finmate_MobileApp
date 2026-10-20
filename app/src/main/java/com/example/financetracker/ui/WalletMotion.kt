package com.example.financetracker.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator

/** Short, finite motion that follows Android's animation setting. */
object WalletMotion {
    private val easing = DecelerateInterpolator(1.6f)
    fun enabled() = ValueAnimator.areAnimatorsEnabled()

    @SuppressLint("ClickableViewAccessibility")
    fun press(view: View) {
        view.setOnTouchListener { target, event ->
            if (enabled() && target.isEnabled) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> target.animate().scaleX(0.975f).scaleY(0.975f).setDuration(90).setStartDelay(0).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> target.animate().scaleX(1f).scaleY(1f).setInterpolator(easing).setDuration(170).setStartDelay(0).start()
                }
            }
            // Keep native clicking, scrolling, keyboard, and accessibility behaviour.
            false
        }
    }

    fun reveal(view: View, delay: Long = 0) {
        if (!enabled()) return
        view.alpha = 0f
        view.translationY = 8f * view.resources.displayMetrics.density
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(260).setInterpolator(easing).start()
        cancelWhenDetached(view)
    }

    fun revealChildren(parent: ViewGroup) {
        if (!enabled()) return
        for (index in 0 until minOf(parent.childCount, 7)) reveal(parent.getChildAt(index), index * 24L)
    }

    fun confirm(view: View) {
        if (!enabled()) return
        view.scaleX = 0.975f; view.scaleY = 0.975f; view.alpha = 0.65f
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(280).setInterpolator(easing).start()
        cancelWhenDetached(view)
    }

    private fun cancelWhenDetached(view: View) {
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) { v.animate().cancel(); v.removeOnAttachStateChangeListener(this) }
        })
    }
}
