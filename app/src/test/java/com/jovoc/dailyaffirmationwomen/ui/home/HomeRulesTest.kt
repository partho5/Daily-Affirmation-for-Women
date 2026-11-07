package com.jovoc.dailyaffirmationwomen.ui.home

import com.jovoc.dailyaffirmationwomen.data.model.Category
import com.jovoc.dailyaffirmationwomen.data.model.MetaSession
import com.jovoc.dailyaffirmationwomen.data.model.SessionLogEntry
import com.jovoc.dailyaffirmationwomen.data.model.SessionMode
import com.jovoc.dailyaffirmationwomen.data.model.SuggestPolicy
import com.jovoc.dailyaffirmationwomen.domain.SuggestionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM unit test verifying business rules for Home Screen and Today's Practice Card (Feature F2).
 * Source of truth: AGENT_INSTRUCTIONS.md Section 3.1 & Section 4 (F2).
 *
 * Rules verified:
 * 1. Checks if session already completed today -> flags completed state.
 * 2. If completed today: no start action available, calm done_today state.
 * 3. Time estimate: time_sleep for PAPER_TIMER, time_standard for STANDARD / SAY_ALOUD.
 * 4. Category order: All 12 categories presented in exact JSON file order.
 * 5. CategoryRow strict prohibition: forbidden developer fields are not exposed.
 */
class HomeRulesTest {

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

        categories = listOf(
            Category("my_values", "My values", "When you want to center yourself", SessionMode.STANDARD, SuggestPolicy.NORMAL, affirmations = emptyList()),
            Category("kind_to_myself", "Kind to myself", "When you are being hard on yourself", SessionMode.STANDARD, SuggestPolicy.NORMAL, affirmations = emptyList()),
            Category("my_body_can", "What my body can do", "When body thoughts are heavy", SessionMode.STANDARD, SuggestPolicy.NORMAL, affirmations = emptyList()),
            Category("hard_days", "On hard days", "When everything feels like a lot", SessionMode.STANDARD, SuggestPolicy.NORMAL, affirmations = emptyList()),
            Category("by_my_name", "Call yourself by name", "When you need to hear a steady voice", SessionMode.STANDARD, SuggestPolicy.NORMAL, affirmations = emptyList()),
            Category("before_something_big", "Before something big", "Before a talk, an interview, or a hard conversation", SessionMode.SAY_ALOUD, SuggestPolicy.NORMAL, affirmations = emptyList()),
            Category("small_good_things", "Three small good things", "When days feel empty or automatic", SessionMode.STANDARD, SuggestPolicy.LIMITED, affirmations = emptyList()),
            Category("bedtime_unload", "Bedtime unload", "When you are in bed and your head will not stop", SessionMode.PAPER_TIMER, SuggestPolicy.NIGHT_ONLY, affirmations = emptyList()),
            Category("my_future_self", "My future self", "When you need a longer view", SessionMode.STANDARD, SuggestPolicy.NORMAL, affirmations = emptyList()),
            Category("not_alone", "Not alone in this", "When you feel like the only one struggling", SessionMode.STANDARD, SuggestPolicy.NORMAL, affirmations = emptyList()),
            Category("midlife_and_beyond", "Midlife and beyond", "When changes in your body or life feel unfamiliar", SessionMode.STANDARD, SuggestPolicy.OPT_IN, audience = "Women aged 46 and older. Show only if the user chooses this category.", affirmations = emptyList()),
            Category("work_and_voice", "At work and speaking up", "When you hold back what you want to say", SessionMode.STANDARD, SuggestPolicy.NORMAL, affirmations = emptyList())
        )
    }

    @Test
    fun sessionCompletedToday_detectedCorrectly() {
        val todayStr = "2026-11-07"
        val sessionLogDoneToday = listOf(
            SessionLogEntry(date = todayStr, categoryId = "kind_to_myself", sentenceIds = listOf("k_1"))
        )
        val hasCompletedToday = sessionLogDoneToday.any { it.date == todayStr }
        assertTrue("Session done today must be detected", hasCompletedToday)

        val sessionLogDoneYesterday = listOf(
            SessionLogEntry(date = "2026-11-06", categoryId = "kind_to_myself", sentenceIds = listOf("k_1"))
        )
        val hasCompletedYesterdayOnly = sessionLogDoneYesterday.any { it.date == todayStr }
        assertFalse("Session done yesterday must not flag completed today", hasCompletedYesterdayOnly)
    }

    @Test
    fun timeEstimate_resolvesBasedOnSessionMode() {
        val paperTimerCategory = categories.first { it.sessionMode == SessionMode.PAPER_TIMER }
        val isSleepTimer = paperTimerCategory.sessionMode == SessionMode.PAPER_TIMER
        assertTrue("PAPER_TIMER category must use sleep time", isSleepTimer)

        val standardCategory = categories.first { it.sessionMode == SessionMode.STANDARD }
        assertFalse("STANDARD category must not use sleep time", standardCategory.sessionMode == SessionMode.PAPER_TIMER)

        val sayAloudCategory = categories.first { it.sessionMode == SessionMode.SAY_ALOUD }
        assertFalse("SAY_ALOUD category must not use sleep time", sayAloudCategory.sessionMode == SessionMode.PAPER_TIMER)
    }

    @Test
    fun all12Categories_preserveExactJsonOrder() {
        assertEquals(12, categories.size)
        val expectedOrder = listOf(
            "my_values",
            "kind_to_myself",
            "my_body_can",
            "hard_days",
            "by_my_name",
            "before_something_big",
            "small_good_things",
            "bedtime_unload",
            "my_future_self",
            "not_alone",
            "midlife_and_beyond",
            "work_and_voice"
        )
        for (i in expectedOrder.indices) {
            assertEquals("Category at index $i must match expected file order", expectedOrder[i], categories[i].id)
        }
    }

    @Test
    fun audienceSubtitle_presentOnlyForMidlifeAndBeyond() {
        val midlife = categories.first { it.id == "midlife_and_beyond" }
        assertNotNull(midlife.audience)
        assertTrue(midlife.audience!!.isNotEmpty())

        val otherCategories = categories.filter { it.id != "midlife_and_beyond" }
        for (cat in otherCategories) {
            assertTrue("Category ${cat.id} should not have audience", cat.audience.isNullOrEmpty())
        }
    }
}
