package com.jovoc.dailyaffirmationwomen.data.repository

import com.jovoc.dailyaffirmationwomen.data.model.Level
import com.jovoc.dailyaffirmationwomen.data.model.SessionMode
import com.jovoc.dailyaffirmationwomen.data.model.SuggestPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class DatasetRepositoryTest {

    @Test
    fun parseJson_validAssetFile_parsesAllCategoriesAndAffirmations() {
        val assetFile = File("src/main/assets/affirmation_dataset.json").let {
            if (it.exists()) it else File("app/src/main/assets/affirmation_dataset.json")
        }
        assertTrue("Asset file must exist", assetFile.exists())

        val jsonString = assetFile.readText()
        val dataset = DatasetRepository.parseJson(jsonString)

        // Meta session
        assertEquals(150, dataset.metaSession.standardTargetSeconds)
        assertEquals(3, dataset.metaSession.maxSentencesPerSession)
        assertEquals(21, dataset.metaSession.nightStartHour)
        assertEquals(5, dataset.metaSession.nightEndHour)
        assertEquals(3, dataset.metaSession.limitedCategoryMinDaysBetweenSuggestions)
        assertEquals(3, dataset.metaSession.noFitAfterNotYetInARow)
        assertEquals(7, dataset.metaSession.avoidRepeatingSentenceDays)
        assertEquals("{name}", dataset.namePlaceholder)

        // Categories count
        assertEquals(12, dataset.categories.size)

        // First category: my_values
        val firstCategory = dataset.categories[0]
        assertEquals("my_values", firstCategory.id)
        assertEquals("My values", firstCategory.title)
        assertEquals(SessionMode.STANDARD, firstCategory.sessionMode)
        assertEquals(SuggestPolicy.NORMAL, firstCategory.suggestPolicy)
        assertEquals(600, firstCategory.deepOptionSeconds)
        assertEquals("values_01", firstCategory.researchNoteId)
        assertEquals(3, firstCategory.affirmations.size)

        // Every category must have required fields
        for (category in dataset.categories) {
            assertTrue("Category ID must not be blank", category.id.isNotBlank())
            assertTrue("Category Title must not be blank", category.title.isNotBlank())
            assertTrue("Category must have at least one affirmation", category.affirmations.isNotEmpty())

            for (aff in category.affirmations) {
                assertTrue("Affirmation ID must not be blank", aff.id.isNotBlank())
                assertNotNull("Must contain gentle level", aff.levels[Level.GENTLE])
                assertNotNull("Must contain medium level", aff.levels[Level.MEDIUM])
                assertNotNull("Must contain strong level", aff.levels[Level.STRONG])
                assertTrue("gentle level must not be blank", aff.levels[Level.GENTLE]!!.isNotBlank())
                assertTrue("medium level must not be blank", aff.levels[Level.MEDIUM]!!.isNotBlank())
                assertTrue("strong level must not be blank", aff.levels[Level.STRONG]!!.isNotBlank())
            }
        }

        // Research notes
        assertEquals(11, dataset.researchNotes.size)
        val note = dataset.researchNotes["values_01"]
        assertNotNull("values_01 note must exist", note)
        assertEquals("values_01", note!!.id)
        assertTrue(note.claimUi.isNotBlank())
        assertTrue(note.population.isNotBlank())
        assertTrue(note.source.isNotBlank())
    }

    @Test
    fun parseJson_missingCategories_throwsIllegalStateException() {
        val invalidJson = """
            {
                "meta": { "name_placeholder": "{name}" }
            }
        """.trimIndent()

        try {
            DatasetRepository.parseJson(invalidJson)
            fail("Expected IllegalStateException for missing categories")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("missing required 'categories' array"))
        }
    }

    @Test
    fun parseJson_emptyCategories_throwsIllegalStateException() {
        val invalidJson = """
            {
                "categories": []
            }
        """.trimIndent()

        try {
            DatasetRepository.parseJson(invalidJson)
            fail("Expected IllegalStateException for empty categories")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("must not be empty"))
        }
    }

    @Test
    fun parseJson_categoryMissingTitle_throwsIllegalStateException() {
        val invalidJson = """
            {
                "categories": [
                    {
                        "id": "test_cat",
                        "title": "",
                        "affirmations": [
                            {
                                "id": "t_01",
                                "levels": { "gentle": "g", "medium": "m", "strong": "s" },
                                "prompt": "p"
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        try {
            DatasetRepository.parseJson(invalidJson)
            fail("Expected IllegalStateException for empty title")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("missing required 'title'"))
        }
    }

    @Test
    fun parseJson_missingGentleLevel_throwsIllegalStateException() {
        val invalidJson = """
            {
                "categories": [
                    {
                        "id": "test_cat",
                        "title": "Test Title",
                        "affirmations": [
                            {
                                "id": "t_01",
                                "levels": { "medium": "m", "strong": "s" },
                                "prompt": "p"
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        try {
            DatasetRepository.parseJson(invalidJson)
            fail("Expected IllegalStateException for missing gentle level")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("levels.gentle"))
        }
    }

    @Test
    fun parseJson_blankStrongLevel_throwsIllegalStateException() {
        val invalidJson = """
            {
                "categories": [
                    {
                        "id": "test_cat",
                        "title": "Test Title",
                        "affirmations": [
                            {
                                "id": "t_01",
                                "levels": { "gentle": "g", "medium": "m", "strong": "   " },
                                "prompt": "p"
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        try {
            DatasetRepository.parseJson(invalidJson)
            fail("Expected IllegalStateException for blank strong level")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("levels.strong"))
        }
    }

    @Test
    fun levelEnum_transitionsAndHelpers() {
        assertEquals(Level.MEDIUM, Level.GENTLE.next())
        assertEquals(Level.STRONG, Level.MEDIUM.next())
        assertEquals(Level.STRONG, Level.STRONG.next())

        assertEquals(Level.MEDIUM, Level.STRONG.previous())
        assertEquals(Level.GENTLE, Level.MEDIUM.previous())
        assertEquals(Level.GENTLE, Level.GENTLE.previous())

        assertEquals(Level.GENTLE, Level.fromString("gentle"))
        assertEquals(Level.MEDIUM, Level.fromString("MEDIUM"))
        assertEquals(Level.STRONG, Level.fromString("strong"))
        assertEquals(Level.GENTLE, Level.fromString("unknown"))

        assertEquals(Level.GENTLE, Level.fromKey("gentle"))
        assertNull(Level.fromKey("unknown"))
    }

    @Test
    fun sessionMode_fromString() {
        assertEquals(SessionMode.STANDARD, SessionMode.fromString("standard"))
        assertEquals(SessionMode.SAY_ALOUD, SessionMode.fromString("say_aloud"))
        assertEquals(SessionMode.PAPER_TIMER, SessionMode.fromString("paper_timer"))
        assertEquals(SessionMode.STANDARD, SessionMode.fromString("other"))
    }

    @Test
    fun suggestPolicy_fromString() {
        assertEquals(SuggestPolicy.NORMAL, SuggestPolicy.fromString("normal"))
        assertEquals(SuggestPolicy.NIGHT_ONLY, SuggestPolicy.fromString("night_only"))
        assertEquals(SuggestPolicy.OPT_IN, SuggestPolicy.fromString("opt_in"))
        assertEquals(SuggestPolicy.LIMITED, SuggestPolicy.fromString("limited"))
        assertEquals(SuggestPolicy.NORMAL, SuggestPolicy.fromString("other"))
    }
}
