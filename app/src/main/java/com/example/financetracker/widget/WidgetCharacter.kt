package com.example.financetracker.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.LruCache
import android.provider.Settings
import android.widget.RemoteViews
import com.example.financetracker.R
import com.example.financetracker.ui.AnimationCharacter
import com.example.financetracker.ui.WalletGuideView
import com.example.financetracker.ui.WalletMotion

/** Small sprite frames rendered once per character/gesture, then flipped by the launcher. */
internal object WidgetCharacter {
    private const val FRAME_SIZE = 128
    private data class Key(val character: AnimationCharacter, val gesture: WalletGuideView.Gesture)
    private val cache = LruCache<Key, List<Bitmap>>(2)

    fun views(context: Context, gesture: WalletGuideView.Gesture, motionEnabled: Boolean =
        WalletMotion.enabled() && Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    ): RemoteViews {
        val frames = frames(context, AnimationCharacter.selected(context), gesture)
        fun frame(index: Int) = RemoteViews(context.packageName, R.layout.widget_character_frame).apply {
            setImageViewBitmap(R.id.widgetCharacterImage, frames[index])
        }
        // Omit ViewFlipper entirely for reduced motion: a single still image has no timer.
        if (!motionEnabled) return frame(gesture.restingFrame)
        return RemoteViews(context.packageName, R.layout.widget_character_motion).apply {
            removeAllViews(R.id.widgetCharacterFlipper)
            // A short gesture between pauses, using four shared 128px images (256 KiB total).
            (List(5) { gesture.restingFrame } + gesture.sequence).forEach { index ->
                addView(R.id.widgetCharacterFlipper, frame(index))
            }
        }
    }

    private fun frames(context: Context, character: AnimationCharacter, gesture: WalletGuideView.Gesture): List<Bitmap> = synchronized(cache) {
        val key = Key(character, gesture)
        cache.get(key) ?: run {
            val atlas = requireNotNull(BitmapFactory.decodeResource(context.resources, character.guideAtlas,
                BitmapFactory.Options().apply { inScaled = false }))
            try {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                val target = RectF(0f, 0f, FRAME_SIZE.toFloat(), FRAME_SIZE.toFloat())
                List(4) { column ->
                    val source = Rect(column * atlas.width / 4, gesture.row * atlas.height / 4,
                        (column + 1) * atlas.width / 4, (gesture.row + 1) * atlas.height / 4)
                    Bitmap.createBitmap(FRAME_SIZE, FRAME_SIZE, Bitmap.Config.ARGB_8888).also { bitmap ->
                        Canvas(bitmap).drawBitmap(atlas, source, target, paint)
                    }
                }.also { cache.put(key, it) }
            } finally { atlas.recycle() }
        }
    }
}
