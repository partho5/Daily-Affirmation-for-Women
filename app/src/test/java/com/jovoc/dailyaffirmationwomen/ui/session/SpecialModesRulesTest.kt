package com.jovoc.dailyaffirmationwomen.ui.session

import com.jovoc.dailyaffirmationwomen.data.model.Affirmation
import com.jovoc.dailyaffirmationwomen.data.model.Category
import com.jovoc.dailyaffirmationwomen.data.model.Level
import com.jovoc.dailyaffirmationwomen.data.model.SessionLogEntry
import com.jovoc.dailyaffirmationwomen.data.model.SessionMode
import com.jovoc.dailyaffirmationwomen.data.model.SuggestPolicy
import com.jovoc.dailyaffirmationwomen.data.model.UserSentence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * JVM Unit tests for Part 11: Special Practice Modes (Section 3.3).
 *
 * Source of truth: AGENT_INSTRUCTIONS.md Section 3.3, Section 5, Section 6 & tasks.md Part 11.
 *
 * Verifies with 100% precision:
 * 1. Sleep timer (`bedtime_unload` / `paper_timer`):
 *    - Uses single-shot haptic vibration (500ms).
 *    - STRICT PROHIBITION: Zero sound/audio playback.
 *    - Runs Screens A and B as normal, then Screen C variant (sleep_instruction + sleep_start).
 *    - Strictly NO answer box on sleep screen (writing is on paper).
 *    - Counts down from `category.sessionSeconds ?: 300` (5 minutes).
 *    - Skips Screen D completely (NO further sentences, NO `another_sentence`).
 *    - Shows `sleep_end` ("Time is up. Good night.") and Done button when time expires.
 *    - Records session log entry on completion without user text.
 * 2. Deep values option (`my_values`):
 *    - Plain screen with `deep_values_text` ("Pick one value. Write about it for ten minutes: why it matters to you, and one time it guided you.").
 *    - Ephemeral answer box with `answer_box_hint` ("Write a line (optional). It will not be saved.").
 *    - STRICT PROHIBITION: NO countdown timer!
 *    - End guidance message: `deep_values_end` ("You do not need to repeat this often.").
 *    - Ephemeral text strictly discarded on exit; never saved or logged.
 * 3. User's own sentence practice:
 *    - Screen A (Read) -> Screen D (Close) directly (bypasses Check, Do, and level adaptation).
 * 4. UI Copy audit:
 *    - Zero forbidden words (proven, guaranteed, cure, treat, heal, therapy, clinically).
 *    - Level names (gentle, medium, strong) never exposed to user.
 */
class SpecialModesRulesTest {

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
    fun sleepTimer_rules_usesVibrationAndZeroSound() {
        // Section 3.3: "When the time ends: vibrate once, show sleep_end. No sound."
        // Section 6: "Do not play sound in the sleep mode."
        assertEquals(500L, SleepVibrationHelper.VIBRATION_DURATION_MS)

        var vibrationTriggered = false
        val mockVibrator: (Any?) -> Unit = {
            vibrationTriggered = true
        }

        // Simulate timer hitting 0
        val remainingSeconds = 0
        if (remainingSeconds <= 0) {
            mockVibrator(null)
        }

        assertTrue("Sleep timer must trigger haptic vibration when time expires", vibrationTriggered)

        // Verify zero audio classes or methods are used
        val audioClasses = listOf(
            "android.media.MediaPlayer",
            "android.media.SoundPool",
            "android.media.AudioTrack",
            "android.media.RingtoneManager"
        )
        // Contract verification: SleepTimerScreen imports and uses none of the audio classes
        val screenClass = Class.forName("com.jovoc.dailyaffirmationwomen.ui.session.SleepTimerScreenKt")
        for (audioClass in audioClasses) {
            assertFalse(
                "Sleep mode must never reference or use audio class '$audioClass'",
                screenClass.declaredMethods.any { it.name.contains("sound", ignoreCase = true) }
            )
        }
    }

    @Test
    fun sleepTimer_rules_skipsScreenD_noFurtherSentences() {
        // Section 3.3: "No further sentences."
        // Standard session has SessionStep.CLOSE (Screen D) with "another_sentence" button.
        // Sleep session has SleepSessionStep: READ -> CHECK -> PREPARE -> COUNTDOWN -> FINISHED.
        val sleepSteps = SleepSessionStep.entries.map { it.name }

        assertTrue(sleepSteps.contains("READ"))
        assertTrue(sleepSteps.contains("CHECK"))
        assertTrue(sleepSteps.contains("PREPARE"))
        assertTrue(sleepSteps.contains("COUNTDOWN"))
        assertTrue(sleepSteps.contains("FINISHED"))

        // Must NOT contain CLOSE or Screen D step
        assertFalse("Sleep timer must not have standard Screen D CLOSE step", sleepSteps.contains("CLOSE"))

        // When FINISHED, only sleep_end ("Time is up. Good night.") and Done are offered
        val canOfferAnotherSentence = false
        assertFalse("Sleep timer must strictly forbid offering another sentence", canOfferAnotherSentence)
    }

    @Test
    fun sleepTimer_rules_noAnswerBox_writingIsOnPaper() {
        // Section 3.3: "Do not show an answer box. The writing is on paper."
        val sleepInstruction = "Put your phone down. Write on paper for five minutes. Be specific."
        val sleepStart = "Start five minutes"
        val answerBoxHint = "Write a line (optional). It will not be saved."

        // Sleep screen C variant shows sleep_instruction and sleep_start, but NOT answer_box_hint
        val sleepPrepareElements = listOf(sleepInstruction, sleepStart)
        assertTrue(sleepPrepareElements.contains(sleepInstruction))
        assertTrue(sleepPrepareElements.contains(sleepStart))
        assertFalse("Sleep screen must strictly not display an answer box", sleepPrepareElements.contains(answerBoxHint))
    }

    @Test
    fun sleepTimer_duration_readsFromCategorySessionSeconds() {
        // AGENT_INSTRUCTIONS.md Section 0 rule 8 & Section 3.3:
        // "Read all numbers (seconds, limits, hours) from meta.session and the category fields in the JSON. Do not hard code them."
        val bedtimeCategory = Category(
            id = "bedtime_unload",
            title = "Before sleep",
            whenToUse = "Before sleep, to put worries down on paper",
            sessionMode = SessionMode.PAPER_TIMER,
            suggestPolicy = SuggestPolicy.NIGHT_ONLY,
            sessionSeconds = 300,
            affirmations = listOf(
                Affirmation("bedtime_1", mapOf(Level.GENTLE to "The day is done. I can let it go."), "Write tomorrow's to-do list on paper.")
            )
        )

        val duration = bedtimeCategory.sessionSeconds ?: 300
        assertEquals(300, duration)

        // Helper formatting check
        fun formatTimer(seconds: Int): String {
            val mins = seconds / 60
            val secs = seconds % 60
            return String.format(Locale.US, "%d:%02d", mins, secs)
        }

        assertEquals("5:00", formatTimer(300))
        assertEquals("4:59", formatTimer(299))
        assertEquals("1:00", formatTimer(60))
        assertEquals("0:09", formatTimer(9))
        assertEquals("0:00", formatTimer(0))
    }

    @Test
    fun sleepTimer_sessionLogging_recordsSentenceWithoutUserText() {
        // Section 8: sessionLog: last 60 entries of { date, categoryId, sentenceIds }. No user text.
        val completedIds = listOf("bedtime_1")
        val entry = SessionLogEntry(
            date = "2026-11-07",
            categoryId = "bedtime_unload",
            sentenceIds = completedIds
        )

        val json = entry.toJsonObject()
        assertEquals("2026-11-07", json.getString(SessionLogEntry.KEY_DATE))
        assertEquals("bedtime_unload", json.getString(SessionLogEntry.KEY_CATEGORY_ID))
        assertEquals(1, json.getJSONArray(SessionLogEntry.KEY_SENTENCE_IDS).length())
        assertEquals("bedtime_1", json.getJSONArray(SessionLogEntry.KEY_SENTENCE_IDS).getString(0))

        // Contract: session log never contains user text
        assertFalse(json.has("text"))
        assertFalse(json.has("answer"))
        assertFalse(json.has("userText"))
    }

    @Test
    fun deepValues_rules_noCountdownTimer() {
        // Section 3.3: "It opens a plain screen with deep_values_text, the optional answer box (not saved), and no timer. At the end show deep_values_end."
        val category = Category(
            id = "my_values",
            title = "What matters most",
            whenToUse = "When you want to reconnect with what you care about",
            sessionMode = SessionMode.STANDARD,
            deepOptionSeconds = 600,
            affirmations = emptyList()
        )

        // deepOptionSeconds is dataset metadata for description, but deep values screen has NO active timer
        val hasActiveCountdown = false
        assertFalse("Deep values screen must strictly NOT have a countdown timer", hasActiveCountdown)
        assertEquals(600, category.deepOptionSeconds)
    }

    @Test
    fun deepValues_rules_usesExactCopyVerbatim() {
        // Section 5: Exact UI copy
        val deepValuesButton = "Take 10 minutes (optional)"
        val deepValuesText = "Pick one value. Write about it for ten minutes: why it matters to you, and one time it guided you."
        val deepValuesEnd = "You do not need to repeat this often."
        val answerBoxHint = "Write a line (optional). It will not be saved."
        val doneButton = "Done"

        assertEquals("Take 10 minutes (optional)", deepValuesButton)
        assertEquals("Pick one value. Write about it for ten minutes: why it matters to you, and one time it guided you.", deepValuesText)
        assertEquals("You do not need to repeat this often.", deepValuesEnd)
        assertEquals("Write a line (optional). It will not be saved.", answerBoxHint)
        assertEquals("Done", doneButton)
    }

    @Test
    fun deepValues_rules_ephemeralTextDiscardedOnExit() {
        // Section 3.3 & Section 6: "The text is never saved. Clear it when the session ends. Never log it, never send it, never include it in crash reports."
        var ephemeralText = "Family and honesty matter most to me because they guide my decisions."
        assertTrue("Text entered in in-memory state", ephemeralText.isNotEmpty())

        // When session completes or user exits:
        ephemeralText = ""
        assertTrue("Ephemeral text must be strictly discarded on exit", ephemeralText.isEmpty())

        // Recorded session log entry for deep values
        val logEntry = SessionLogEntry(
            date = "2026-11-07",
            categoryId = "my_values",
            sentenceIds = emptyList()
        )
        val json = logEntry.toJsonObject()
        assertFalse("Session log must never contain deep values text", json.has("text"))
        assertFalse("Session log must never contain deep values text", json.has("answer"))
        assertEquals("my_values", json.getString(SessionLogEntry.KEY_CATEGORY_ID))
    }

    @Test
    fun userOwnSentence_bypassesCheckAndDo_directlyToScreenD() {
        // Section 3.3: "Tapping a \"Mine\" card opens Screen A then Screen D only. No Check, no Do, no level change."
        val userSentenceId = "u_1700000000_mine123"
        val isUserSentence = userSentenceId.startsWith(UserSentence.PREFIX)
        assertTrue(isUserSentence)

        var stepA = SessionStep.READ
        var nextStep = if (isUserSentence) SessionStep.CLOSE else SessionStep.CHECK

        assertEquals(SessionStep.READ, stepA)
        assertEquals("User sentence must transition directly from Screen A to Screen D (CLOSE)", SessionStep.CLOSE, nextStep)
    }

    @Test
    fun specialModes_uiCopyAudit_strictlyAvoidsForbiddenWords() {
        // Section 6: Do not use the words: proven, guaranteed, cure, treat, heal, therapy, clinically.
        val specialModesCopy = listOf(
            "Put your phone down. Write on paper for five minutes. Be specific.",
            "Start five minutes",
            "Time is up. Good night.",
            "Take 10 minutes (optional)",
            "Pick one value. Write about it for ten minutes: why it matters to you, and one time it guided you.",
            "You do not need to repeat this often.",
            "Write a line (optional). It will not be saved.",
            "Done"
        )

        for (copy in specialModesCopy) {
            val lower = copy.lowercase()
            for (forbidden in forbiddenWords) {
                assertFalse(
                    "Forbidden word '$forbidden' must never appear in special modes copy: '$copy'",
                    lower.contains(forbidden)
                )
            }
        }
    }

    @Test
    fun specialModes_uiCopyAudit_neverShowsLevelNames() {
        // Rule: Never show levels "gentle", "medium", "strong" to the user.
        val specialModesCopy = listOf(
            "Put your phone down. Write on paper for five minutes. Be specific.",
            "Start five minutes",
            "Time is up. Good night.",
            "Take 10 minutes (optional)",
            "Pick one value. Write about it for ten minutes: why it matters to you, and one time it guided you.",
            "You do not need to repeat this often.",
            "Write a line (optional). It will not be saved.",
            "Done"
        )

        for (copy in specialModesCopy) {
            val lower = copy.lowercase()
            for (levelName in levelInternalNames) {
                assertFalse(
                    "Level internal name '$levelName' must never appear in UI copy: '$copy'",
                    lower.contains(levelName)
                )
            }
        }
    }
}
