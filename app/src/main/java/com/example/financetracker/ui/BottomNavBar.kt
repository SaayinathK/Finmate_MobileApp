package com.example.financetracker.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import com.example.financetracker.R

class BottomNavBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private var selectedItemId: Int = ITEM_HOME
    private var listener: ((Int) -> Unit)? = null

    private val items = listOf(ITEM_HOME, ITEM_HISTORY, ITEM_ANALYSIS, ITEM_LOAN, ITEM_SETTINGS)

    init {
        orientation = HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.nav_bottom, this, true)
        setupClickListeners()
        setSelectedItem(selectedItemId)
    }

    private fun setupClickListeners() {
        items.forEach { itemId ->
            findViewById<View>(itemId)?.setOnClickListener {
                if (itemId != selectedItemId) {
                    setSelectedItem(itemId)
                    listener?.invoke(itemId)
                }
            }
        }
    }

    fun setOnItemSelectedListener(l: (Int) -> Unit) {
        listener = l
    }

    fun setSelectedItem(@IdRes itemId: Int) {
        items.forEach { id ->
            updateItemVisuals(id, id == itemId)
        }
        selectedItemId = itemId
    }

    private fun updateItemVisuals(@IdRes itemId: Int, isSelected: Boolean) {
        val layout = findViewById<LinearLayout>(itemId)
        val icon = layout.getChildAt(0) as? ImageView
        val text = layout.getChildAt(1) as? TextView

        val colorRes = if (isSelected) R.color.blue_700 else R.color.light_gray
        val color = ContextCompat.getColor(context, colorRes)

        icon?.setColorFilter(color)
        text?.setTextColor(color)
    }

    companion object {
        @JvmField val ITEM_HOME = R.id.home
        @JvmField val ITEM_HISTORY = R.id.history
        @JvmField val ITEM_ANALYSIS = R.id.analysis
        @JvmField val ITEM_LOAN = R.id.loan
        @JvmField val ITEM_SETTINGS = R.id.setting
    }
}
