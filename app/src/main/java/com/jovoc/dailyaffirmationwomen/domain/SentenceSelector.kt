package com.jovoc.dailyaffirmationwomen.domain

import com.jovoc.dailyaffirmationwomen.data.model.Affirmation
import com.jovoc.dailyaffirmationwomen.data.model.Category
import com.jovoc.dailyaffirmationwomen.data.model.SessionLogEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Result of formatting an affirmation sentence that may contain `{name}`.
 */
sealed class FormattedSentence {
    data class Ready(val text: String) : FormattedSentence()
    data object NeedsNamePrompt : FormattedSentence()
}

/**
 * Pure Kotlin sentence selection and formatting logic.
 *
 * Implements:
 * - Next sentence selection avoiding recently practiced sentences (Section 3.2 Screen D).
 * - Name placeholder formatting and name prompt requirement (Section 4 F3, Section 6).
 */
object SentenceSelector {

    const val DEFAULT_AVOID_DAYS = 7
    const val DEFAULT_NAME_PLACEHOLDER = "{name}"

    /**
     * Selects the next sentence to practice in [category].
     *
     * 1. Evaluates built-in affirmations in file order from [category.affirmations].
     * 2. Finds all sentence IDs practiced in this category within [avoidDays] of [todayDateStr].
     * 3. Picks the first affirmation in file order whose ID was NOT practiced in last [avoidDays].
     * 4. Fallback if all were practiced: picks the one practiced longest ago (oldest practice date).
     */
    fun getNextSentence(
        category: Category,
        sessionLog: List<SessionLogEntry>,
        todayDateStr: String,
        avoidDays: Int = DEFAULT_AVOID_DAYS
    ): Affirmation {
        require(category.affirmations.isNotEmpty()) {
            "Category '${category.id}' contains no affirmations"
        }

        val today = try {
            LocalDate.parse(todayDateStr)
        } catch (_: Exception) {
            null
        }

        // 1. Identify sentence IDs practiced within avoidDays
        val practicedRecentlyIds = mutableSetOf<String>()
        if (today != null && avoidDays > 0) {
            for (entry in sessionLog) {
                if (entry.categoryId == category.id) {
                    val entryDate = try {
                        LocalDate.parse(entry.date)
                    } catch (_: Exception) {
                        null
                    } ?: continue

                    val daysBetween = ChronoUnit.DAYS.between(entryDate, today)
                    if (daysBetween in 0 until avoidDays.toLong()) {
                        practicedRecentlyIds.addAll(entry.sentenceIds)
                    }
                }
            }
        }

        // 2. Pick the first unpracticed affirmation in file order
        val unpracticed = category.affirmations.firstOrNull { it.id !in practicedRecentlyIds }
        if (unpracticed != null) {
            return unpracticed
        }

        // 3. Fallback: find the affirmation whose most recent practice was longest ago
        val affirmationWithLatestPractice = category.affirmations.map { affirmation ->
            val latestPracticeDate = sessionLog
                .filter { it.categoryId == category.id && affirmation.id in it.sentenceIds }
                .mapNotNull { entry ->
                    try {
                        LocalDate.parse(entry.date)
                    } catch (_: Exception) {
                        null
                    }
                }
                .maxOrNull()
            affirmation to latestPracticeDate
        }

        val oldestPracticed = affirmationWithLatestPractice.minByOrNull { (_, latestDate) ->
            latestDate ?: LocalDate.MIN
        }?.first

        return oldestPracticed ?: category.affirmations.first()
    }

    /**
     * Formats an affirmation sentence, replacing [namePlaceholder] with [userName].
     *
     * - If [rawText] does NOT contain [namePlaceholder]: returns [FormattedSentence.Ready].
     * - Else if [userName] is present and not blank: replaces [namePlaceholder] with [userName] and returns [FormattedSentence.Ready].
     * - Else: returns [FormattedSentence.NeedsNamePrompt] to signal UI to display name prompt card.
     */
    fun formatSentence(
        rawText: String,
        userName: String?,
        namePlaceholder: String = DEFAULT_NAME_PLACEHOLDER
    ): FormattedSentence {
        if (!rawText.contains(namePlaceholder)) {
            return FormattedSentence.Ready(rawText)
        }
        val trimmedName = userName?.trim()
        return if (!trimmedName.isNullOrEmpty()) {
            FormattedSentence.Ready(rawText.replace(namePlaceholder, trimmedName))
        } else {
            FormattedSentence.NeedsNamePrompt
        }
    }
}

fun getNextSentence(
    category: Category,
    sessionLog: List<SessionLogEntry>,
    todayDateStr: String,
    avoidDays: Int = SentenceSelector.DEFAULT_AVOID_DAYS
): Affirmation = SentenceSelector.getNextSentence(
    category = category,
    sessionLog = sessionLog,
    todayDateStr = todayDateStr,
    avoidDays = avoidDays
)

fun formatSentence(
    rawText: String,
    userName: String?,
    namePlaceholder: String = SentenceSelector.DEFAULT_NAME_PLACEHOLDER
): FormattedSentence = SentenceSelector.formatSentence(
    rawText = rawText,
    userName = userName,
    namePlaceholder = namePlaceholder
)
