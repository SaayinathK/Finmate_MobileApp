package com.example.financetracker.ui

import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.financetracker.R
import com.example.financetracker.activities.*
import com.google.android.material.bottomnavigation.BottomNavigationView

object BottomNavHelper {
    fun attach(activity: Activity) {
        // Legacy XML screens need system bar insets too on Android 15+.
        if (activity !is WalletScreen) {
            val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)
            if (root != null && root.getTag(R.id.insetsApplied) == null) {
                root.setTag(R.id.insetsApplied, true)
                val left = root.paddingLeft; val top = root.paddingTop; val right = root.paddingRight; val bottom = root.paddingBottom
                ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
                    val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
                    view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom); insets
                }; ViewCompat.requestApplyInsets(root)
            }
        }
        val nav = activity.findViewById<View>(R.id.bottomNav) ?: return
        fun navigate(id: Int) {
            val target = when(id) { R.id.home -> HomeActivity::class.java; R.id.history -> TransactionHistoryActivity::class.java; R.id.analysis -> TasksActivity::class.java; R.id.loan -> LoansActivity::class.java; R.id.setting -> ProfileActivity::class.java; else -> null }
            if (id == R.id.setting && activity is SettingsActivity) return
            if (target != null && activity.javaClass != target) activity.startActivity(Intent(activity, target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
        val selected = when(activity) { is HomeActivity -> R.id.home; is TransactionHistoryActivity -> R.id.history; is TasksActivity -> R.id.analysis; is LoansActivity -> R.id.loan; is SettingsActivity -> R.id.setting; else -> 0 }
        if (nav is BottomNavBar) { nav.setOnItemSelectedListener { navigate(it) }; nav.setSelectedItem(selected) }
        if (nav is BottomNavigationView) {
            if (selected != 0) nav.selectedItemId = selected
            nav.setOnItemSelectedListener { navigate(it.itemId); true }
        }
    }
}
