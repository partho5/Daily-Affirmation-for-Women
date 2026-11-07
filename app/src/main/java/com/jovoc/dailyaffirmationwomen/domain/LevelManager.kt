package com.jovoc.dailyaffirmationwomen.domain

import com.jovoc.dailyaffirmationwomen.data.model.Level

/**
 * Result of adapting practice level following user feedback.
 *
 * CRITICAL RULE:
 * Level names ("gentle", "medium", "strong") must NEVER be shown to the user in any UI element.
 */
data class LevelAdaptationResult(
    val newLevel: Level,
    val newStreak: Int,
    val showCategoryNoFit: Boolean
)

/**
 * Pure Kotlin level adaptation manager.
 *
 * Implements Section 3.2 Screen B:
 * - "Feels true": moves category up one level (gentle -> medium -> strong; capped at strong),
 *   resets consecutive "Not yet" streak to 0, showCategoryNoFit = false.
 * - "Not yet": moves category down one level (strong -> medium -> gentle; capped at gentle),
 *   increments consecutive "Not yet" streak.
 *   If streak reaches [noFitThreshold] (default 3) while at gentle, triggers showCategoryNoFit = true once.
 */
object LevelManager {

    const val DEFAULT_NO_FIT_THRESHOLD = 3

    fun onFeelsTrue(currentLevel: Level): LevelAdaptationResult {
        return LevelAdaptationResult(
            newLevel = currentLevel.next(),
            newStreak = 0,
            showCategoryNoFit = false
        )
    }

    fun onNotYet(
        currentLevel: Level,
        currentStreak: Int,
        noFitThreshold: Int = DEFAULT_NO_FIT_THRESHOLD
    ): LevelAdaptationResult {
        val newLevel = currentLevel.previous()
        val newStreak = currentStreak + 1
        val showCategoryNoFit = (currentLevel == Level.GENTLE && newStreak >= noFitThreshold)
        return LevelAdaptationResult(
            newLevel = newLevel,
            newStreak = newStreak,
            showCategoryNoFit = showCategoryNoFit
        )
    }
}

fun onFeelsTrue(currentLevel: Level): LevelAdaptationResult =
    LevelManager.onFeelsTrue(currentLevel)

fun onNotYet(
    currentLevel: Level,
    currentStreak: Int,
    noFitThreshold: Int = LevelManager.DEFAULT_NO_FIT_THRESHOLD
): LevelAdaptationResult = LevelManager.onNotYet(currentLevel, currentStreak, noFitThreshold)
