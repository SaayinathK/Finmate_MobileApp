package com.example.financetracker.ui

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.financetracker.R

/** One local choice shared by startup, guide gestures and achievement celebrations. */
enum class AnimationCharacter(
    val preferenceValue: String,
    @StringRes val label: Int,
    @DrawableRes val guideAtlas: Int,
    @DrawableRes val runnerAtlas: Int
) {
    MALE("male", R.string.animation_male, R.drawable.swallet_guide_atlas, R.drawable.swallet_runner_atlas),
    FEMALE("female", R.string.animation_female, R.drawable.swallet_female_guide_atlas, R.drawable.swallet_female_runner_atlas);

    companion object {
        internal const val PREFERENCE_KEY = "animation_gender"
        private fun preferences(context: Context) = context.getSharedPreferences("finmate_settings", Context.MODE_PRIVATE)

        fun selected(context: Context): AnimationCharacter {
            val value = preferences(context).getString(PREFERENCE_KEY, null)
            return entries.firstOrNull { it.preferenceValue == value } ?: MALE
        }

        fun select(context: Context, character: AnimationCharacter) {
            preferences(context).edit().putString(PREFERENCE_KEY, character.preferenceValue).apply()
        }
    }
}
