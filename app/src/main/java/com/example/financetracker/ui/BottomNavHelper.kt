package com.example.financetracker.ui

import android.app.Activity
import android.content.Intent
import android.view.View
import com.example.financetracker.R
import com.example.financetracker.activities.*
import com.google.android.material.bottomnavigation.BottomNavigationView

object BottomNavHelper {

    fun attach(activity: Activity) {
        var navView: View? = activity.findViewById(R.id.bottomNav)
        if (navView == null) {
            val legacyNavId = activity.resources.getIdentifier("bottomNavigation", "id", activity.packageName)
            if (legacyNavId != 0) navView = activity.findViewById(legacyNavId)
        }

        if (navView != null && navView.tag != "bottom_nav_attached") {
            navView.tag = "bottom_nav_attached"

            if (navView is BottomNavigationView) {
                // Logic for Material BottomNavigationView
                navView.setOnItemSelectedListener { item ->
                    handleMaterialNav(activity, item.itemId)
                    true
                }
                updateMaterialSelection(activity, navView)

            } else if (navView is BottomNavBar) {
                // Logic for custom BottomNavBar
                navView.setOnItemSelectedListener { id ->
                    handleCustomNav(activity, id)
                }
                updateCustomSelection(activity, navView)
            }
        }
    }

    private fun handleMaterialNav(activity: Activity, itemId: Int) {
        when (itemId) {
            R.id.home -> if (activity !is HomeActivity) activity.startActivity(Intent(activity, HomeActivity::class.java))
            R.id.history -> if (activity !is TransactionHistoryActivity) activity.startActivity(Intent(activity, TransactionHistoryActivity::class.java))
            R.id.analysis -> if (activity !is CategoryAnalysisActivity) activity.startActivity(Intent(activity, CategoryAnalysisActivity::class.java))
            R.id.setting -> if (activity !is SettingsActivity) activity.startActivity(Intent(activity, SettingsActivity::class.java))
        }
    }

    private fun handleCustomNav(activity: Activity, itemId: Int) {
        when (itemId) {
            BottomNavBar.ITEM_HOME -> if (activity !is HomeActivity) activity.startActivity(Intent(activity, HomeActivity::class.java))
            BottomNavBar.ITEM_HISTORY -> if (activity !is TransactionHistoryActivity) activity.startActivity(Intent(activity, TransactionHistoryActivity::class.java))
            BottomNavBar.ITEM_ANALYSIS -> if (activity !is CategoryAnalysisActivity) activity.startActivity(Intent(activity, CategoryAnalysisActivity::class.java))
            BottomNavBar.ITEM_LOAN -> if (activity !is LoansActivity) activity.startActivity(Intent(activity, LoansActivity::class.java))
            BottomNavBar.ITEM_SETTINGS -> if (activity !is SettingsActivity) activity.startActivity(Intent(activity, SettingsActivity::class.java))
        }
    }

    private fun updateMaterialSelection(activity: Activity, nav: BottomNavigationView) {
        val currentItem = when (activity) {
            is HomeActivity -> R.id.home
            is TransactionHistoryActivity -> R.id.history
            is CategoryAnalysisActivity -> R.id.analysis
            is SettingsActivity -> R.id.setting
            else -> 0
        }
        if (currentItem != 0) {
            nav.selectedItemId = currentItem
        }
    }

    private fun updateCustomSelection(activity: Activity, nav: BottomNavBar) {
        val currentItem = when (activity) {
            is HomeActivity -> BottomNavBar.ITEM_HOME
            is TransactionHistoryActivity -> BottomNavBar.ITEM_HISTORY
            is CategoryAnalysisActivity -> BottomNavBar.ITEM_ANALYSIS
            is LoansActivity -> BottomNavBar.ITEM_LOAN
            is SettingsActivity -> BottomNavBar.ITEM_SETTINGS
            else -> 0
        }
        if (currentItem != 0) {
            nav.setSelectedItem(currentItem)
        }
    }
}
