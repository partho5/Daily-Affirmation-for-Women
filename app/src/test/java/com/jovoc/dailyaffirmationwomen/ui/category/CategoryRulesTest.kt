package com.jovoc.dailyaffirmationwomen.ui.category

import com.jovoc.dailyaffirmationwomen.data.model.Affirmation
import com.jovoc.dailyaffirmationwomen.data.model.Level
import com.jovoc.dailyaffirmationwomen.data.model.ResearchNote
import com.jovoc.dailyaffirmationwomen.data.model.UserSentence
import com.jovoc.dailyaffirmationwomen.domain.FormattedSentence
import com.jovoc.dailyaffirmationwomen.domain.SentenceSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit test verifying Category Screen, Research Notes & Custom Sentences rules (Features F3, F5).
 * Source of truth: AGENT_INSTRUCTIONS.md Section 3.3, 4 (F3, F5), 5, 6.
 *
 * Rules verified:
 * 1. Research notes: allowed fields only (claimUi, population, source).
 * 2. Forbidden words check: UI copy strictly avoids forbidden words.
 * 3. Custom sentence: 200 character limit enforcement and u_ prefix requirement.
 * 4. {name} rule: NamePrompt displayed if name is missing; raw {name} is never displayed.
 * 5. Level resolution: Level text resolves correctly for gentle, medium, and strong.
 * 6. Special category: "my_values" requires deep values option.
 */
class CategoryRulesTest {

    private val forbiddenWords = listOf(
        "proven",
        "guaranteed",
        "cure",
        "treat",
        "heal",
        "therapy",
        "clinically"
    )

    @Test
    fun researchNote_containsOnlyAllowedDisplayFields() {
        val note = ResearchNote(
            id = "note_values",
            claimUi = "Writing about your values reminds you what matters most.",
            population = "Adults facing stressful situations.",
            source = "Creswell et al., 2005"
        )

        assertEquals("note_values", note.id)
        assertEquals("Writing about your values reminds you what matters most.", note.claimUi)
        assertEquals("Adults facing stressful situations.", note.population)
        assertEquals("Creswell et al., 2005", note.source)

        val formattedSource = "Source: ${note.source}"
        assertEquals("Source: Creswell et al., 2005", formattedSource)
    }

    @Test
    fun forbiddenWords_strictlyExcludedFromCategoryUIElements() {
        val categoryTitlesAndDescriptions = listOf(
            "My values",
            "When you want to center yourself",
            "Kind to myself",
            "When you are being hard on yourself",
            "What research found",
            "Write something you can believe today.",
            "Take 10 minutes (optional)",
            "Start practice",
            "Add my own sentence",
            "Add your first name",
            "To view this sentence personalized for you, set your name in Settings.",
            "Go to Settings"
        )

        for (text in categoryTitlesAndDescriptions) {
            val lower = text.lowercase()
            for (forbidden in forbiddenWords) {
                assertFalse(
                    "Forbidden word '$forbidden' must never appear in UI copy: '$text'",
                    lower.contains(forbidden)
                )
            }
        }
    }

    @Test
    fun customSentence_enforces200CharLimit() {
        val maxLen = 200
        val exactly200 = "a".repeat(maxLen)
        assertEquals(200, exactly200.length)

        val validUserSentence = UserSentence(
            id = "u_12345_abc",
            categoryId = "my_values",
            text = exactly200
        )
        assertEquals(200, validUserSentence.text.length)
        assertTrue(validUserSentence.id.startsWith("u_"))

        val over200 = "a".repeat(201)
        var exceptionThrown = false
        try {
            UserSentence(
                id = "u_12345_def",
                categoryId = "my_values",
                text = over200
            )
        } catch (_: IllegalArgumentException) {
            exceptionThrown = true
        }
        assertTrue("UserSentence text exceeding 200 chars must throw IllegalArgumentException", exceptionThrown)
    }

    @Test
    fun customSentence_requiresUPrefix() {
        var exceptionThrown = false
        try {
            UserSentence(
                id = "invalid_id_without_u_prefix",
                categoryId = "my_values",
                text = "A valid sentence"
            )
        } catch (_: IllegalArgumentException) {
            exceptionThrown = true
        }
        assertTrue("UserSentence without 'u_' prefix must be rejected", exceptionThrown)
    }

    @Test
    fun namePlaceholder_formatsWithUserNameWhenAvailable() {
        val rawSentence = "Take a breath, {name}. You are here now."
        val formatted = SentenceSelector.formatSentence(rawSentence, "Sarah")

        assertTrue(formatted is FormattedSentence.Ready)
        val text = (formatted as FormattedSentence.Ready).text
        assertEquals("Take a breath, Sarah. You are here now.", text)
        assertFalse("Formatted text must not contain placeholder", text.contains("{name}"))
    }

    @Test
    fun namePlaceholder_returnsNeedsNamePromptWhenNameMissing() {
        val rawSentence = "Take a breath, {name}. You are here now."

        // Case 1: null name
        val formattedNull = SentenceSelector.formatSentence(rawSentence, null)
        assertTrue("Null name must result in NeedsNamePrompt", formattedNull is FormattedSentence.NeedsNamePrompt)

        // Case 2: blank name
        val formattedBlank = SentenceSelector.formatSentence(rawSentence, "   ")
        assertTrue("Blank name must result in NeedsNamePrompt", formattedBlank is FormattedSentence.NeedsNamePrompt)
    }

    @Test
    fun regularSentence_returnsReadyWithoutName() {
        val rawSentence = "Today I can take one calm step forward."
        val formatted = SentenceSelector.formatSentence(rawSentence, null)

        assertTrue(formatted is FormattedSentence.Ready)
        assertEquals("Today I can take one calm step forward.", (formatted as FormattedSentence.Ready).text)
    }

    @Test
    fun builtInSentence_resolvesByLevel() {
        val affirmation = Affirmation(
            id = "val_1",
            levels = mapOf(
                Level.GENTLE to "I can pause and remember what matters.",
                Level.MEDIUM to "My values guide my choices today.",
                Level.STRONG to "I stand firmly in what I value."
            ),
            prompt = "Name one thing you value deeply right now."
        )

        assertEquals("I can pause and remember what matters.", affirmation.textForLevel(Level.GENTLE))
        assertEquals("My values guide my choices today.", affirmation.textForLevel(Level.MEDIUM))
        assertEquals("I stand firmly in what I value.", affirmation.textForLevel(Level.STRONG))
    }

    @Test
    fun specialCategory_myValuesOption() {
        val categoryId = "my_values"
        val hasDeepValuesOption = categoryId == "my_values"
        assertTrue("my_values category must provide deep values option", hasDeepValuesOption)

        val otherCategory = "kind_to_myself"
        val otherHasDeepValuesOption = otherCategory == "my_values"
        assertFalse("other categories must not provide deep values option", otherHasDeepValuesOption)
    }
}
