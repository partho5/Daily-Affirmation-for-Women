package com.jovoc.dailyaffirmationwomen.domain

import com.jovoc.dailyaffirmationwomen.data.model.Affirmation
import com.jovoc.dailyaffirmationwomen.data.model.Category
import com.jovoc.dailyaffirmationwomen.data.model.Level
import com.jovoc.dailyaffirmationwomen.data.model.MetaSession
import com.jovoc.dailyaffirmationwomen.data.model.SessionLogEntry
import com.jovoc.dailyaffirmationwomen.data.model.SuggestPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for SuggestionEngine, SentenceSelector, and LevelManager.
 *
 * Verifies with 100% precision:
 * 1. First launch ever returns "kind_to_myself"
 * 2. Night hour (22:00) with no session done today returns "bedtime_unload"
 * 3. Night hour with session already done today does not return night category if rotated
 * 4. Normal rotation advances to next category in file order
 * 5. Opt-in category ("midlife_and_beyond") skipped unless opened previously
 * 6. Limited category ("small_good_things") skipped if practiced within 3 days
 * 7. SentenceSelector picking unpracticed sentence, fallback to oldest, and formatting with/without name
 * 8. LevelManager transitions and category_no_fit trigger at 3 consecutive "Not yet" at gentle.
 */
class SuggestionEngineTest {

    private lateinit var metaSession: MetaSession
    private lateinit var categories: List<Category>

    @Before
    fun setUp() {
        metaSession = MetaSession(
            standardTargetSeconds = 150,
            maxSentencesPerSession = 3,
            nightStartHour = 21,
            nightEndHour = 5,
            limitedCategoryMinDaysBetweenSuggestions = 3,
            noFitAfterNotYetInARow = 3,
            avoidRepeatingSentenceDays = 7
        )

        // Standard 12 categories matching affirmation_dataset.json in file order
        categories = listOf(
            createCategory("my_values", SuggestPolicy.NORMAL),
            createCategory("kind_to_myself", SuggestPolicy.NORMAL),
            createCategory("my_body_can", SuggestPolicy.NORMAL),
            createCategory("hard_days", SuggestPolicy.NORMAL),
            createCategory("by_my_name", SuggestPolicy.NORMAL),
            createCategory("before_something_big", SuggestPolicy.NORMAL),
            createCategory("small_good_things", SuggestPolicy.LIMITED),
            createCategory("bedtime_unload", SuggestPolicy.NIGHT_ONLY),
            createCategory("my_future_self", SuggestPolicy.NORMAL),
            createCategory("not_alone", SuggestPolicy.NORMAL),
            createCategory("midlife_and_beyond", SuggestPolicy.OPT_IN),
            createCategory("work_and_voice", SuggestPolicy.NORMAL)
        )
    }

    private fun createCategory(
        id: String,
        suggestPolicy: SuggestPolicy = SuggestPolicy.NORMAL,
        affirmations: List<Affirmation> = listOf(
            Affirmation(
                id = "${id}_1",
                levels = mapOf(Level.GENTLE to "Gentle $id 1", Level.MEDIUM to "Medium $id 1", Level.STRONG to "Strong $id 1"),
                prompt = "Prompt $id 1"
            ),
            Affirmation(
                id = "${id}_2",
                levels = mapOf(Level.GENTLE to "Gentle $id 2", Level.MEDIUM to "Medium $id 2", Level.STRONG to "Strong $id 2"),
                prompt = "Prompt $id 2"
            ),
            Affirmation(
                id = "${id}_3",
                levels = mapOf(Level.GENTLE to "Gentle $id 3", Level.MEDIUM to "Medium $id 3", Level.STRONG to "Strong $id 3"),
                prompt = "Prompt $id 3"
            )
        )
    ): Category {
        return Category(
            id = id,
            title = id.replace('_', ' ').replaceFirstChar { it.uppercase() },
            whenToUse = "When to use $id",
            suggestPolicy = suggestPolicy,
            affirmations = affirmations
        )
    }

    // ------------------------------------------------------------------------
    // 1. First Launch Ever
    // ------------------------------------------------------------------------

    @Test
    fun rule1_firstLaunchEver_returnsKindToMyself() {
        val suggested = SuggestionEngine.getSuggestedCategory(
            currentHour = 10,
            todayDateStr = "2026-11-07",
            sessionLog = emptyList(),
            openedCategories = emptySet(),
            categories = categories,
            metaSession = metaSession
        )
        assertEquals("kind_to_myself", suggested)
    }

    @Test
    fun rule1_firstLaunchEver_atNight_stillReturnsKindToMyself() {
        // First launch ever rule takes precedence over night rule
        val suggested = SuggestionEngine.getSuggestedCategory(
            currentHour = 22,
            todayDateStr = "2026-11-07",
            sessionLog = emptyList(),
            openedCategories = emptySet(),
            categories = categories,
            metaSession = metaSession
        )
        assertEquals("kind_to_myself", suggested)
    }

    // ------------------------------------------------------------------------
    // 2. Night Window
    // ------------------------------------------------------------------------

    @Test
    fun rule2_nightHour22_noSessionToday_returnsBedtimeUnload() {
        val suggested = SuggestionEngine.getSuggestedCategory(
            currentHour = 22,
            todayDateStr = "2026-11-07",
            sessionLog = listOf(
                SessionLogEntry("2026-11-06", "my_values", listOf("v_001"))
            ),
            openedCategories = setOf("my_values"),
            categories = categories,
            metaSession = metaSession
        )
        assertEquals("bedtime_unload", suggested)
    }

    @Test
    fun rule2_nightHours_allHoursInWindow_returnBedtimeUnload() {
        // Window is 21:00 to 04:59 (hours 21, 22, 23, 0, 1, 2, 3, 4)
        val nightHours = listOf(21, 22, 23, 0, 1, 2, 3, 4)
        for (hour in nightHours) {
            val suggested = SuggestionEngine.getSuggestedCategory(
                currentHour = hour,
                todayDateStr = "2026-11-07",
                sessionLog = listOf(
                    SessionLogEntry("2026-11-06", "my_values", listOf("v_001"))
                ),
                openedCategories = setOf("my_values"),
                categories = categories,
                metaSession = metaSession
            )
            assertEquals("Expected bedtime_unload for night hour $hour", "bedtime_unload", suggested)
        }
    }

    @Test
    fun rule2_dayHours_doNotTriggerNightRule() {
        // Window ends at 5 (05:00), so 5, 12, 20 are day hours
        val dayHours = listOf(5, 6, 12, 17, 20)
        for (hour in dayHours) {
            val suggested = SuggestionEngine.getSuggestedCategory(
                currentHour = hour,
                todayDateStr = "2026-11-07",
                sessionLog = listOf(
                    SessionLogEntry("2026-11-06", "my_values", listOf("v_001"))
                ),
                openedCategories = setOf("my_values"),
                categories = categories,
                metaSession = metaSession
            )
            // After my_values (index 0) comes kind_to_myself (index 1)
            assertEquals("Expected daytime rotation for hour $hour", "kind_to_myself", suggested)
        }
    }

    // ------------------------------------------------------------------------
    // 3. Night Hour With Session Already Done Today
    // ------------------------------------------------------------------------

    @Test
    fun rule2_nightHour_withSessionDoneToday_doesNotReturnNightCategory() {
        // Session already finished today (2026-11-07) on "small_good_things"
        // It should NOT suggest "bedtime_unload" via night rule; rotation should advance to "my_future_self"
        val suggested = SuggestionEngine.getSuggestedCategory(
            currentHour = 22,
            todayDateStr = "2026-11-07",
            sessionLog = listOf(
                SessionLogEntry("2026-11-07", "small_good_things", listOf("small_good_things_1"))
            ),
            openedCategories = setOf("small_good_things"),
            categories = categories,
            metaSession = metaSession
        )
        // small_good_things is index 6. Index 7 is bedtime_unload (NIGHT_ONLY -> skipped in rotation).
        // Next is index 8: my_future_self
        assertEquals("my_future_self", suggested)
    }

    // ------------------------------------------------------------------------
    // 4. Normal Rotation
    // ------------------------------------------------------------------------

    @Test
    fun rule3_normalRotation_advancesToNextCategory() {
        // After "my_values" (index 0) -> "kind_to_myself" (index 1)
        val suggested1 = SuggestionEngine.getSuggestedCategory(
            currentHour = 14,
            todayDateStr = "2026-11-07",
            sessionLog = listOf(
                SessionLogEntry("2026-11-06", "my_values", listOf("my_values_1"))
            ),
            openedCategories = setOf("my_values"),
            categories = categories,
            metaSession = metaSession
        )
        assertEquals("kind_to_myself", suggested1)

        // After "kind_to_myself" (index 1) -> "my_body_can" (index 2)
        val suggested2 = SuggestionEngine.getSuggestedCategory(
            currentHour = 14,
            todayDateStr = "2026-11-07",
            sessionLog = listOf(
                SessionLogEntry("2026-11-06", "kind_to_myself", listOf("kind_to_myself_1"))
            ),
            openedCategories = setOf("kind_to_myself"),
            categories = categories,
            metaSession = metaSession
        )
        assertEquals("my_body_can", suggested2)
    }

    @Test
    fun rule3_normalRotation_wrapsAroundAtEnd() {
        // Last category in file order is "work_and_voice" (index 11)
        // Wrapping around should return "my_values" (index 0)
        val suggested = SuggestionEngine.getSuggestedCategory(
            currentHour = 14,
            todayDateStr = "2026-11-07",
            sessionLog = listOf(
                SessionLogEntry("2026-11-06", "work_and_voice", listOf("work_and_voice_1"))
            ),
            openedCategories = setOf("work_and_voice"),
            categories = categories,
            metaSession = metaSession
        )
        assertEquals("my_values", suggested)
    }

    // ------------------------------------------------------------------------
    // 5. Opt-in Category Handling
    // ------------------------------------------------------------------------

    @Test
    fun rule3_optIn_skippedUnlessOpenedPreviously() {
        // "not_alone" is index 9.
        // Index 10 is "midlife_and_beyond" (OPT_IN).
        // Index 11 is "work_and_voice" (NORMAL).

        // Case A: midlife_and_beyond NOT in openedCategories -> skipped, returns work_and_voice
        val suggestedSkipped = SuggestionEngine.getSuggestedCategory(
            currentHour = 14,
            todayDateStr = "2026-11-07",
            sessionLog = listOf(
                SessionLogEntry("2026-11-06", "not_alone", listOf("not_alone_1"))
            ),
            openedCategories = setOf("not_alone"), // NOT containing midlife_and_beyond
            categories = categories,
            metaSession = metaSession
        )
        assertEquals("work_and_voice", suggestedSkipped)

        // Case B: midlife_and_beyond IS in openedCategories -> returns midlife_and_beyond
        val suggestedOpened = SuggestionEngine.getSuggestedCategory(
            currentHour = 14,
            todayDateStr = "2026-11-07",
            sessionLog = listOf(
                SessionLogEntry("2026-11-06", "not_alone", listOf("not_alone_1"))
            ),
            openedCategories = setOf("not_alone", "midlife_and_beyond"),
            categories = categories,
            metaSession = metaSession
        )
        assertEquals("midlife_and_beyond", suggestedOpened)
    }

    // ------------------------------------------------------------------------
    // 6. Limited Category Handling
    // ------------------------------------------------------------------------

    @Test
    fun rule3_limitedCategory_skippedIfPracticedWithin3Days() {
        // "before_something_big" is index 5.
        // Index 6 is "small_good_things" (LIMITED, min 3 days).
        // Index 7 is "bedtime_unload" (NIGHT_ONLY -> skipped).
        // Index 8 is "my_future_self" (NORMAL).

        // Practiced 1 day ago (2026-11-06) relative to today (2026-11-07) -> skipped!
        val suggested1Day = SuggestionEngine.getSuggestedCategory(
            currentHour = 14,
            todayDateStr = "2026-11-07",
            sessionLog = listOf(
                SessionLogEntry("2026-11-06", "before_something_big", listOf("b_1")),
                SessionLogEntry("2026-11-06", "small_good_things", listOf("s_1"))
            ),
            openedCategories = setOf("before_something_big", "small_good_things"),
            categories = categories,
            metaSession = metaSession
        )
        assertEquals("my_future_self", suggested1Day)

        // Practiced 2 days ago (2026-11-05) relative to today (2026-11-07) -> skipped!
        val suggested2Days = SuggestionEngine.getSuggestedCategory(
            currentHour = 14,
            todayDateStr = "2026-11-07",
            sessionLog = listOf(
                SessionLogEntry("2026-11-06", "before_something_big", listOf("b_1")),
                SessionLogEntry("2026-11-05", "small_good_things", listOf("s_1"))
            ),
            openedCategories = setOf("before_something_big", "small_good_things"),
            categories = categories,
            metaSession = metaSession
        )
        assertEquals("my_future_self", suggested2Days)

        // Practiced 3 days ago (2026-11-04) relative to today (2026-11-07) -> NOT skipped (eligible!)
        val suggested3Days = SuggestionEngine.getSuggestedCategory(
            currentHour = 14,
            todayDateStr = "2026-11-07",
            sessionLog = listOf(
                SessionLogEntry("2026-11-06", "before_something_big", listOf("b_1")),
                SessionLogEntry("2026-11-04", "small_good_things", listOf("s_1"))
            ),
            openedCategories = setOf("before_something_big", "small_good_things"),
            categories = categories,
            metaSession = metaSession
        )
        assertEquals("small_good_things", suggested3Days)

        // Never practiced before -> eligible!
        val suggestedNeverPracticed = SuggestionEngine.getSuggestedCategory(
            currentHour = 14,
            todayDateStr = "2026-11-07",
            sessionLog = listOf(
                SessionLogEntry("2026-11-06", "before_something_big", listOf("b_1"))
            ),
            openedCategories = setOf("before_something_big"),
            categories = categories,
            metaSession = metaSession
        )
        assertEquals("small_good_things", suggestedNeverPracticed)
    }

    // ------------------------------------------------------------------------
    // 7. SentenceSelector Tests
    // ------------------------------------------------------------------------

    @Test
    fun sentenceSelector_picksUnpracticedSentenceInOrder() {
        val category = categories.first { it.id == "kind_to_myself" }
        // category affirmations: kind_to_myself_1, kind_to_myself_2, kind_to_myself_3

        // When no sessions practiced, returns first sentence in file order
        val sentence1 = SentenceSelector.getNextSentence(
            category = category,
            sessionLog = emptyList(),
            todayDateStr = "2026-11-07"
        )
        assertEquals("kind_to_myself_1", sentence1.id)

        // When sentence 1 was practiced yesterday, returns sentence 2
        val sentence2 = SentenceSelector.getNextSentence(
            category = category,
            sessionLog = listOf(
                SessionLogEntry("2026-11-06", "kind_to_myself", listOf("kind_to_myself_1"))
            ),
            todayDateStr = "2026-11-07"
        )
        assertEquals("kind_to_myself_2", sentence2.id)

        // When sentences 1 & 2 were practiced within 7 days, returns sentence 3
        val sentence3 = SentenceSelector.getNextSentence(
            category = category,
            sessionLog = listOf(
                SessionLogEntry("2026-11-06", "kind_to_myself", listOf("kind_to_myself_1")),
                SessionLogEntry("2026-11-05", "kind_to_myself", listOf("kind_to_myself_2"))
            ),
            todayDateStr = "2026-11-07"
        )
        assertEquals("kind_to_myself_3", sentence3.id)
    }

    @Test
    fun sentenceSelector_fallbackToOldestWhenAllPracticedRecently() {
        val category = categories.first { it.id == "kind_to_myself" }
        // All 3 sentences were practiced within last 7 days:
        // sentence 1 practiced 2026-11-07 (0 days ago)
        // sentence 2 practiced 2026-11-06 (1 day ago)
        // sentence 3 practiced 2026-11-04 (3 days ago - oldest practice date)
        val sessionLog = listOf(
            SessionLogEntry("2026-11-07", "kind_to_myself", listOf("kind_to_myself_1")),
            SessionLogEntry("2026-11-06", "kind_to_myself", listOf("kind_to_myself_2")),
            SessionLogEntry("2026-11-04", "kind_to_myself", listOf("kind_to_myself_3"))
        )

        val fallback = SentenceSelector.getNextSentence(
            category = category,
            sessionLog = sessionLog,
            todayDateStr = "2026-11-07",
            avoidDays = 7
        )
        assertEquals("kind_to_myself_3", fallback.id)
    }

    @Test
    fun sentenceSelector_formatSentence_withAndWithoutName() {
        // 1. Text without {name} placeholder
        val rawNoName = "Making a mistake is part of being human."
        val resultNoName = SentenceSelector.formatSentence(rawNoName, userName = null)
        assertTrue(resultNoName is FormattedSentence.Ready)
        assertEquals(rawNoName, (resultNoName as FormattedSentence.Ready).text)

        // 2. Text with {name} and valid user name
        val rawWithName = "{name}, what is one small next step?"
        val resultWithName = SentenceSelector.formatSentence(rawWithName, userName = " Clara ")
        assertTrue(resultWithName is FormattedSentence.Ready)
        assertEquals("Clara, what is one small next step?", (resultWithName as FormattedSentence.Ready).text)

        // 3. Text with {name} and null user name -> NeedsNamePrompt
        val resultNullName = SentenceSelector.formatSentence(rawWithName, userName = null)
        assertEquals(FormattedSentence.NeedsNamePrompt, resultNullName)

        // 4. Text with {name} and blank/empty user name -> NeedsNamePrompt
        val resultEmptyName = SentenceSelector.formatSentence(rawWithName, userName = "")
        assertEquals(FormattedSentence.NeedsNamePrompt, resultEmptyName)

        val resultBlankName = SentenceSelector.formatSentence(rawWithName, userName = "   ")
        assertEquals(FormattedSentence.NeedsNamePrompt, resultBlankName)
    }

    // ------------------------------------------------------------------------
    // 8. LevelManager Tests
    // ------------------------------------------------------------------------

    @Test
    fun levelManager_onFeelsTrue_transitions() {
        // GENTLE -> MEDIUM, streak = 0, noFit = false
        val step1 = LevelManager.onFeelsTrue(Level.GENTLE)
        assertEquals(Level.MEDIUM, step1.newLevel)
        assertEquals(0, step1.newStreak)
        assertFalse(step1.showCategoryNoFit)

        // MEDIUM -> STRONG, streak = 0, noFit = false
        val step2 = LevelManager.onFeelsTrue(Level.MEDIUM)
        assertEquals(Level.STRONG, step2.newLevel)
        assertEquals(0, step2.newStreak)
        assertFalse(step2.showCategoryNoFit)

        // STRONG -> capped at STRONG, streak = 0, noFit = false
        val step3 = LevelManager.onFeelsTrue(Level.STRONG)
        assertEquals(Level.STRONG, step3.newLevel)
        assertEquals(0, step3.newStreak)
        assertFalse(step3.showCategoryNoFit)
    }

    @Test
    fun levelManager_onNotYet_transitions() {
        // STRONG -> MEDIUM, streak increments
        val step1 = LevelManager.onNotYet(Level.STRONG, currentStreak = 0)
        assertEquals(Level.MEDIUM, step1.newLevel)
        assertEquals(1, step1.newStreak)
        assertFalse(step1.showCategoryNoFit)

        // MEDIUM -> GENTLE, streak increments
        val step2 = LevelManager.onNotYet(Level.MEDIUM, currentStreak = 1)
        assertEquals(Level.GENTLE, step2.newLevel)
        assertEquals(2, step2.newStreak)
        assertFalse(step2.showCategoryNoFit)

        // GENTLE -> stays at GENTLE (capped)
        val step3 = LevelManager.onNotYet(Level.GENTLE, currentStreak = 2)
        assertEquals(Level.GENTLE, step3.newLevel)
        assertEquals(3, step3.newStreak)
        // At gentle and streak reached threshold 3 -> showCategoryNoFit = true!
        assertTrue(step3.showCategoryNoFit)
    }

    @Test
    fun levelManager_categoryNoFit_triggersAtThreshold3AtGentle() {
        // Starting at gentle with 0 streak
        val tap1 = LevelManager.onNotYet(Level.GENTLE, currentStreak = 0, noFitThreshold = 3)
        assertEquals(Level.GENTLE, tap1.newLevel)
        assertEquals(1, tap1.newStreak)
        assertFalse("Streak 1 must not trigger category_no_fit", tap1.showCategoryNoFit)

        val tap2 = LevelManager.onNotYet(Level.GENTLE, currentStreak = 1, noFitThreshold = 3)
        assertEquals(Level.GENTLE, tap2.newLevel)
        assertEquals(2, tap2.newStreak)
        assertFalse("Streak 2 must not trigger category_no_fit", tap2.showCategoryNoFit)

        val tap3 = LevelManager.onNotYet(Level.GENTLE, currentStreak = 2, noFitThreshold = 3)
        assertEquals(Level.GENTLE, tap3.newLevel)
        assertEquals(3, tap3.newStreak)
        assertTrue("Streak 3 at gentle MUST trigger category_no_fit", tap3.showCategoryNoFit)

        // Reset streak on "Feels true"
        val reset = LevelManager.onFeelsTrue(Level.GENTLE)
        assertEquals(Level.MEDIUM, reset.newLevel)
        assertEquals(0, reset.newStreak)
        assertFalse(reset.showCategoryNoFit)
    }
}
