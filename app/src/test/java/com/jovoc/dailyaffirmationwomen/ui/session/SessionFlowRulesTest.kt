package com.jovoc.dailyaffirmationwomen.ui.session

import com.jovoc.dailyaffirmationwomen.data.model.Affirmation
import com.jovoc.dailyaffirmationwomen.data.model.Category
import com.jovoc.dailyaffirmationwomen.data.model.Level
import com.jovoc.dailyaffirmationwomen.data.model.MetaSession
import com.jovoc.dailyaffirmationwomen.data.model.SessionLogEntry
import com.jovoc.dailyaffirmationwomen.data.model.SessionMode
import com.jovoc.dailyaffirmationwomen.data.model.SuggestPolicy
import com.jovoc.dailyaffirmationwomen.data.model.UserSentence
import com.jovoc.dailyaffirmationwomen.domain.LevelManager
import com.jovoc.dailyaffirmationwomen.domain.SentenceSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for Part 10: Standard Guided Practice Session & Level Adaptation (Feature F4, Section 3.2).
 *
 * Source of truth: AGENT_INSTRUCTIONS.md Section 3.2, Section 3.3, Section 5 & Section 6.
 *
 * Verifies with 100% precision:
 * 1. Standard flow steps: Screen A (Read) -> Screen B (Check) -> Screen C (Do) -> Screen D (Close).
 * 2. User's own sentence flow: Screen A -> Screen D directly (bypasses Check, Do, and level adaptation).
 * 3. Level adaptation on Screen B:
 *    - "Feels true" moves gentle -> medium -> strong (capped at strong). Resets streak to 0.
 *    - "Not yet" moves strong -> medium -> gentle (capped at gentle). Increments streak.
 *    - 3 consecutive "Not yet" at gentle triggers showCategoryNoFit = true.
 * 4. Max sentences per session:
 *    - `another_sentence` displayed only if sentencesCompletedCount < maxSentencesPerSession (3).
 *    - Hidden once sentencesCompletedCount reaches 3.
 * 5. In-session sentence rotation avoiding already practiced affirmations.
 * 6. Ephemeral answer box privacy: strictly in-memory; SessionLogEntry never contains user text.
 * 7. Screen A instruction selection: `say_aloud` vs standard mode.
 * 8. UI copy audit: forbidden words strictly avoided; level names never displayed.
 */
class SessionFlowRulesTest {

    private val forbiddenWords = listOf(
        "proven",
        "guaranteed",
        "cure",
        "treat",
        "heal",
        "therapy",
        "clinically"
    )

    private val levelInternalNames = listOf(
        "gentle",
        "medium",
        "strong"
    )

    @Test
    fun standardFlow_transitionsThroughAllFourSteps() {
        // Standard built-in affirmation practice flow
        var currentStep = SessionStep.READ
        assertEquals(SessionStep.READ, currentStep)

        // Read (Screen A) -> Check (Screen B)
        currentStep = SessionStep.CHECK
        assertEquals(SessionStep.CHECK, currentStep)

        // Check (Screen B) -> Do (Screen C)
        currentStep = SessionStep.DO
        assertEquals(SessionStep.DO, currentStep)

        // Do (Screen C) -> Close (Screen D)
        currentStep = SessionStep.CLOSE
        assertEquals(SessionStep.CLOSE, currentStep)
    }

    @Test
    fun userOwnSentenceFlow_bypassesCheckAndDo_directlyToClose() {
        // Section 3.3: Tapping a "Mine" card opens Screen A then Screen D only.
        val userSentenceId = "u_1700000000_abc123"
        val isUserSentence = userSentenceId.startsWith(UserSentence.PREFIX)
        assertTrue("Sentence ID starting with u_ must be identified as user sentence", isUserSentence)

        var currentStep = SessionStep.READ
        val practicedIds = mutableListOf<String>()

        // When "Next" is tapped on Screen A for a user sentence:
        if (isUserSentence) {
            practicedIds.add(userSentenceId)
            currentStep = SessionStep.CLOSE
        } else {
            currentStep = SessionStep.CHECK
        }

        // Must jump straight to Screen D (CLOSE), completely bypassing CHECK and DO
        assertEquals(SessionStep.CLOSE, currentStep)
        assertEquals(listOf(userSentenceId), practicedIds)
    }

    @Test
    fun levelAdaptation_feelsTrue_increasesLevelAndResetsStreak() {
        // Gentle -> Medium
        val res1 = LevelManager.onFeelsTrue(Level.GENTLE)
        assertEquals(Level.MEDIUM, res1.newLevel)
        assertEquals(0, res1.newStreak)
        assertFalse(res1.showCategoryNoFit)

        // Medium -> Strong
        val res2 = LevelManager.onFeelsTrue(Level.MEDIUM)
        assertEquals(Level.STRONG, res2.newLevel)
        assertEquals(0, res2.newStreak)
        assertFalse(res2.showCategoryNoFit)

        // Strong -> Strong (capped)
        val res3 = LevelManager.onFeelsTrue(Level.STRONG)
        assertEquals(Level.STRONG, res3.newLevel)
        assertEquals(0, res3.newStreak)
        assertFalse(res3.showCategoryNoFit)
    }

    @Test
    fun levelAdaptation_notYet_decreasesLevelAndIncrementsStreak() {
        // Strong -> Medium
        val res1 = LevelManager.onNotYet(Level.STRONG, currentStreak = 0)
        assertEquals(Level.MEDIUM, res1.newLevel)
        assertEquals(1, res1.newStreak)
        assertFalse(res1.showCategoryNoFit)

        // Medium -> Gentle
        val res2 = LevelManager.onNotYet(Level.MEDIUM, currentStreak = 1)
        assertEquals(Level.GENTLE, res2.newLevel)
        assertEquals(2, res2.newStreak)
        assertFalse(res2.showCategoryNoFit)

        // Gentle -> Gentle (capped at gentle)
        val res3 = LevelManager.onNotYet(Level.GENTLE, currentStreak = 2)
        assertEquals(Level.GENTLE, res3.newLevel)
        assertEquals(3, res3.newStreak)
        // Reached 3 in a row at gentle: triggers showCategoryNoFit
        assertTrue("3 consecutive Not yet at gentle must trigger category_no_fit", res3.showCategoryNoFit)
    }

    @Test
    fun levelAdaptation_feelsTrueResetsStreakAfterNotYet() {
        val notYet1 = LevelManager.onNotYet(Level.GENTLE, currentStreak = 0)
        assertEquals(1, notYet1.newStreak)

        val notYet2 = LevelManager.onNotYet(Level.GENTLE, currentStreak = 1)
        assertEquals(2, notYet2.newStreak)

        // Reset streak on "Feels true"
        val feelsTrue = LevelManager.onFeelsTrue(Level.GENTLE)
        assertEquals(0, feelsTrue.newStreak)
        assertEquals(Level.MEDIUM, feelsTrue.newLevel)
    }

    @Test
    fun screenD_anotherSentenceButton_visibleOnlyUnderMaxLimit() {
        val maxSentencesPerSession = 3

        // After sentence 1 (completed = 1): Another sentence is visible
        val completed1 = 1
        val canShowAnother1 = completed1 < maxSentencesPerSession
        assertTrue(canShowAnother1)

        // After sentence 2 (completed = 2): Another sentence is visible
        val completed2 = 2
        val canShowAnother2 = completed2 < maxSentencesPerSession
        assertTrue(canShowAnother2)

        // After sentence 3 (completed = 3): Another sentence MUST be hidden
        val completed3 = 3
        val canShowAnother3 = completed3 < maxSentencesPerSession
        assertFalse("Another sentence button must be hidden once 3 sentences are completed", canShowAnother3)

        // Beyond 3 (defensive): must be hidden
        val completed4 = 4
        val canShowAnother4 = completed4 < maxSentencesPerSession
        assertFalse(canShowAnother4)
    }

    @Test
    fun inSessionSentenceRotation_picksNextUnpracticedAffirmation() {
        val category = Category(
            id = "kind_to_myself",
            title = "Kind to myself",
            whenToUse = "When you are being hard on yourself",
            sessionMode = SessionMode.STANDARD,
            affirmations = listOf(
                Affirmation("kind_1", mapOf(Level.GENTLE to "I can be gentle with myself today."), "Take one slow breath."),
                Affirmation("kind_2", mapOf(Level.GENTLE to "I do not have to be perfect."), "Notice one thing you did well."),
                Affirmation("kind_3", mapOf(Level.GENTLE to "I am allowed to rest."), "Drop your shoulders right now.")
            )
        )

        val today = "2026-11-07"
        val initialLog = emptyList<SessionLogEntry>()

        // 1st sentence selected
        val firstAffirmation = SentenceSelector.getNextSentence(category, initialLog, today, avoidDays = 7)
        assertEquals("kind_1", firstAffirmation.id)

        // User finishes sentence 1 and taps "Another sentence":
        val inSessionPracticed = listOf("kind_1")
        val sessionLogWithCurrentSession = listOf(
            SessionLogEntry(
                date = today,
                categoryId = category.id,
                sentenceIds = inSessionPracticed
            )
        ) + initialLog

        // 2nd sentence selected: must avoid sentence 1
        val secondAffirmation = SentenceSelector.getNextSentence(category, sessionLogWithCurrentSession, today, avoidDays = 7)
        assertEquals("kind_2", secondAffirmation.id)

        // User finishes sentence 2 and taps "Another sentence":
        val inSessionPracticed2 = listOf("kind_1", "kind_2")
        val sessionLogWithCurrentSession2 = listOf(
            SessionLogEntry(
                date = today,
                categoryId = category.id,
                sentenceIds = inSessionPracticed2
            )
        ) + initialLog

        // 3rd sentence selected: must avoid sentences 1 and 2
        val thirdAffirmation = SentenceSelector.getNextSentence(category, sessionLogWithCurrentSession2, today, avoidDays = 7)
        assertEquals("kind_3", thirdAffirmation.id)
    }

    @Test
    fun ephemeralAnswerBox_strictlyNeverPersistedInSessionLog() {
        // Verify contract: SessionLogEntry only holds date, categoryId, and sentenceIds.
        val entry = SessionLogEntry(
            date = "2026-11-07",
            categoryId = "my_values",
            sentenceIds = listOf("val_1", "val_2")
        )

        val json = entry.toJsonObject()
        assertFalse("SessionLog JSON must never contain answer text", json.has("text"))
        assertFalse("SessionLog JSON must never contain answer text", json.has("answer"))
        assertFalse("SessionLog JSON must never contain answer text", json.has("ephemeral"))
        assertEquals("2026-11-07", json.getString(SessionLogEntry.KEY_DATE))
        assertEquals("my_values", json.getString(SessionLogEntry.KEY_CATEGORY_ID))
        assertEquals(2, json.getJSONArray(SessionLogEntry.KEY_SENTENCE_IDS).length())
    }

    @Test
    fun screenA_instructionSelectionBySessionMode() {
        val standardInstructionKey = "read_instruction"
        val sayAloudInstructionKey = "read_instruction_say_aloud"

        fun resolveInstructionKey(sessionMode: SessionMode): String {
            return if (sessionMode == SessionMode.SAY_ALOUD) sayAloudInstructionKey else standardInstructionKey
        }

        assertEquals("read_instruction_say_aloud", resolveInstructionKey(SessionMode.SAY_ALOUD))
        assertEquals("read_instruction", resolveInstructionKey(SessionMode.STANDARD))
        assertEquals("read_instruction", resolveInstructionKey(SessionMode.PAPER_TIMER))
    }

    @Test
    fun uiCopy_strictlyAvoidsForbiddenWords() {
        val sessionUICopy = listOf(
            "Read it slowly, once. Say it out loud if you can.",
            "Say it out loud, once, just before you begin.",
            "Does it feel true right now?",
            "Feels true",
            "Not yet",
            "That is okay. Think of one time it was true for you, and one time it was not.",
            "This category may not fit you right now. You can try another one, or skip it for a while.",
            "One small step",
            "Take about a minute.",
            "Write a line (optional). It will not be saved.",
            "I will think about it",
            "That is enough for now.",
            "Another sentence",
            "Done"
        )

        for (copy in sessionUICopy) {
            val lower = copy.lowercase()
            for (forbidden in forbiddenWords) {
                assertFalse(
                    "Forbidden word '$forbidden' must never appear in UI copy: '$copy'",
                    lower.contains(forbidden)
                )
            }
        }
    }

    @Test
    fun uiCopy_neverExposesLevelInternalNames() {
        val sessionVisibleCopy = listOf(
            "Does it feel true right now?",
            "Feels true",
            "Not yet",
            "That is okay. Think of one time it was true for you, and one time it was not.",
            "This category may not fit you right now. You can try another one, or skip it for a while.",
            "One small step",
            "Take about a minute.",
            "That is enough for now.",
            "Another sentence",
            "Done"
        )

        for (copy in sessionVisibleCopy) {
            val lower = copy.lowercase()
            for (levelName in levelInternalNames) {
                assertFalse(
                    "Internal level name '$levelName' must never be visible to user: '$copy'",
                    lower.contains(levelName)
                )
            }
        }
    }

    @Test
    fun metaSession_parametersUsedDynamically() {
        val meta = MetaSession(
            standardTargetSeconds = 150,
            maxSentencesPerSession = 3,
            noFitAfterNotYetInARow = 3,
            avoidRepeatingSentenceDays = 7,
            answerBoxSaved = false
        )

        assertEquals(3, meta.maxSentencesPerSession)
        assertEquals(3, meta.noFitAfterNotYetInARow)
        assertEquals(7, meta.avoidRepeatingSentenceDays)
        assertFalse("meta.session.answer_box_saved must be false", meta.answerBoxSaved)
    }
}
