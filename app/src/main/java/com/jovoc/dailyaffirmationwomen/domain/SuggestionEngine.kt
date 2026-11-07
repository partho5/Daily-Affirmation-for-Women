package com.jovoc.dailyaffirmationwomen.domain

import com.jovoc.dailyaffirmationwomen.data.model.Category
import com.jovoc.dailyaffirmationwomen.data.model.MetaSession
import com.jovoc.dailyaffirmationwomen.data.model.SessionLogEntry
import com.jovoc.dailyaffirmationwomen.data.model.SuggestPolicy
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pure Kotlin suggestion engine for Today's practice card.
 *
 * Implements the exact category suggestion rules defined in AGENT_INSTRUCTIONS.md Section 3.1:
 * 1. First launch ever: return "kind_to_myself".
 * 2. Night window: if local hour is within nightStartHour..nightEndHour (21:00 to 04:59)
 *    and no session has been done today: suggest category with suggest_policy = night_only.
 * 3. Rotation: suggest the next category in file order after the last practiced category.
 *    - Skip categories with suggest_policy = night_only.
 *    - Skip opt_in categories unless present in openedCategories.
 *    - Skip limited categories if practiced within limitedCategoryMinDaysBetweenSuggestions days.
 *    - Wrap around circular order.
 *
 * CRITICAL ARCHITECTURE RULE:
 * Zero Android framework dependencies (pure Kotlin). Highly deterministic and unit-testable.
 */
object SuggestionEngine {

    const val DEFAULT_FALLBACK_CATEGORY_ID = "kind_to_myself"
    const val DEFAULT_NIGHT_CATEGORY_ID = "bedtime_unload"

    fun getSuggestedCategory(
        currentHour: Int,
        todayDateStr: String,
        sessionLog: List<SessionLogEntry>,
        openedCategories: Set<String>,
        categories: List<Category>,
        metaSession: MetaSession
    ): String {
        if (categories.isEmpty()) {
            return DEFAULT_FALLBACK_CATEGORY_ID
        }

        // Rule 1: First launch ever (empty sessionLog and empty openedCategories)
        if (sessionLog.isEmpty() && openedCategories.isEmpty()) {
            return categories.firstOrNull { it.id == DEFAULT_FALLBACK_CATEGORY_ID }?.id
                ?: DEFAULT_FALLBACK_CATEGORY_ID
        }

        // Rule 2: Night window (e.g. 21:00 to 04:59) and no session done today
        val isNight = isNightWindow(currentHour, metaSession.nightStartHour, metaSession.nightEndHour)
        val hasSessionToday = sessionLog.any { it.date == todayDateStr }

        if (isNight && !hasSessionToday) {
            val nightCategory = categories.firstOrNull { it.suggestPolicy == SuggestPolicy.NIGHT_ONLY }
            if (nightCategory != null) {
                return nightCategory.id
            }
            val fallbackNight = categories.firstOrNull { it.id == DEFAULT_NIGHT_CATEGORY_ID }
            if (fallbackNight != null) {
                return fallbackNight.id
            }
        }

        // Rule 3: Rotation
        // Find the last practiced category from sessionLog (most recent entry)
        val lastPracticedCategoryId = sessionLog.firstOrNull()?.categoryId
        val lastIndex = if (lastPracticedCategoryId != null) {
            categories.indexOfFirst { it.id == lastPracticedCategoryId }
        } else {
            -1
        }

        val startIndex = if (lastIndex != -1) {
            (lastIndex + 1) % categories.size
        } else {
            0
        }

        // Circular evaluation of candidate categories
        for (offset in 0 until categories.size) {
            val candidateIndex = (startIndex + offset) % categories.size
            val candidate = categories[candidateIndex]

            // Skip NIGHT_ONLY categories in regular rotation
            if (candidate.suggestPolicy == SuggestPolicy.NIGHT_ONLY) {
                continue
            }

            // Skip OPT_IN categories unless user previously opened them
            if (candidate.suggestPolicy == SuggestPolicy.OPT_IN && candidate.id !in openedCategories) {
                continue
            }

            // Skip LIMITED categories if practiced within limitedCategoryMinDaysBetweenSuggestions days
            if (candidate.suggestPolicy == SuggestPolicy.LIMITED &&
                wasCategoryPracticedWithinDays(
                    candidate.id,
                    sessionLog,
                    todayDateStr,
                    metaSession.limitedCategoryMinDaysBetweenSuggestions
                )
            ) {
                continue
            }

            // Candidate passes all checks
            return candidate.id
        }

        // Fallback: if all eligible are skipped, return the next category in order that is not night-only
        for (offset in 0 until categories.size) {
            val fallbackCandidate = categories[(startIndex + offset) % categories.size]
            if (fallbackCandidate.suggestPolicy != SuggestPolicy.NIGHT_ONLY) {
                return fallbackCandidate.id
            }
        }

        return categories.firstOrNull()?.id ?: DEFAULT_FALLBACK_CATEGORY_ID
    }

    /**
     * Determines whether [hour] (0-23) falls within the night window [startHour]..[endHour].
     * Example: startHour = 21, endHour = 5 -> hours 21, 22, 23, 0, 1, 2, 3, 4 are night.
     */
    fun isNightWindow(hour: Int, startHour: Int, endHour: Int): Boolean {
        return if (startHour > endHour) {
            hour >= startHour || hour < endHour
        } else {
            hour in startHour until endHour
        }
    }

    /**
     * Checks if a category was practiced within the last [minDays] days relative to [todayDateStr].
     */
    fun wasCategoryPracticedWithinDays(
        categoryId: String,
        sessionLog: List<SessionLogEntry>,
        todayDateStr: String,
        minDays: Int
    ): Boolean {
        if (minDays <= 0) return false
        val today = try {
            LocalDate.parse(todayDateStr)
        } catch (_: Exception) {
            return false
        }

        for (entry in sessionLog) {
            if (entry.categoryId == categoryId) {
                val entryDate = try {
                    LocalDate.parse(entry.date)
                } catch (_: Exception) {
                    null
                } ?: continue

                val daysBetween = ChronoUnit.DAYS.between(entryDate, today)
                if (daysBetween in 0 until minDays.toLong()) {
                    return true
                }
            }
        }
        return false
    }
}

fun getSuggestedCategory(
    currentHour: Int,
    todayDateStr: String,
    sessionLog: List<SessionLogEntry>,
    openedCategories: Set<String>,
    categories: List<Category>,
    metaSession: MetaSession
): String = SuggestionEngine.getSuggestedCategory(
    currentHour = currentHour,
    todayDateStr = todayDateStr,
    sessionLog = sessionLog,
    openedCategories = openedCategories,
    categories = categories,
    metaSession = metaSession
)
