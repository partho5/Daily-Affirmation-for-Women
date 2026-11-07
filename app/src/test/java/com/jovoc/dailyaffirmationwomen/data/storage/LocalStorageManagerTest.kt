package com.jovoc.dailyaffirmationwomen.data.storage

import android.content.SharedPreferences
import com.jovoc.dailyaffirmationwomen.data.model.Level
import com.jovoc.dailyaffirmationwomen.data.model.SessionLogEntry
import com.jovoc.dailyaffirmationwomen.data.model.UserSentence
import com.jovoc.dailyaffirmationwomen.data.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * Unit test suite verifying the LocalStorageManager contract, serialization,
 * reactive state emissions, and strict constraint adherence.
 */
class LocalStorageManagerTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var storageManager: LocalStorageManager

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        storageManager = LocalStorageManager(fakePrefs)
    }

    // ------------------------------------------------------------------------
    // 1. Serialization & Deserialization Tests
    // ------------------------------------------------------------------------

    @Test
    fun serialization_userSettings_roundTrip() {
        val original = UserSettings(
            name = "Jane",
            onboardingAnswer = "I believe it",
            howItWorksSeen = true
        )
        val serialized = LocalStorageManager.serializeSettings(original)
        val deserialized = LocalStorageManager.parseSettings(serialized)

        assertEquals("Jane", deserialized.name)
        assertEquals("I believe it", deserialized.onboardingAnswer)
        assertTrue(deserialized.howItWorksSeen)

        // Test with nulls
        val nullSettings = UserSettings(name = null, onboardingAnswer = null, howItWorksSeen = false)
        val nullSerialized = LocalStorageManager.serializeSettings(nullSettings)
        val nullDeserialized = LocalStorageManager.parseSettings(nullSerialized)

        assertNull(nullDeserialized.name)
        assertNull(nullDeserialized.onboardingAnswer)
        assertFalse(nullDeserialized.howItWorksSeen)
    }

    @Test
    fun serialization_categoryLevels_roundTrip() {
        val original = mapOf(
            "my_values" to Level.STRONG,
            "kind_to_myself" to Level.MEDIUM,
            "hard_days" to Level.GENTLE
        )
        val serialized = LocalStorageManager.serializeCategoryLevels(original)
        val deserialized = LocalStorageManager.parseCategoryLevels(serialized)

        assertEquals(3, deserialized.size)
        assertEquals(Level.STRONG, deserialized["my_values"])
        assertEquals(Level.MEDIUM, deserialized["kind_to_myself"])
        assertEquals(Level.GENTLE, deserialized["hard_days"])
    }

    @Test
    fun serialization_notYetStreak_roundTrip() {
        val original = mapOf(
            "my_values" to 2,
            "bedtime_unload" to 0,
            "small_good_things" to 3
        )
        val serialized = LocalStorageManager.serializeNotYetStreak(original)
        val deserialized = LocalStorageManager.parseNotYetStreak(serialized)

        assertEquals(3, deserialized.size)
        assertEquals(2, deserialized["my_values"])
        assertEquals(0, deserialized["bedtime_unload"])
        assertEquals(3, deserialized["small_good_things"])
    }

    @Test
    fun serialization_favorites_roundTrip() {
        val original = linkedSetOf("v_001", "k_002", "u_1700000000_abc123")
        val serialized = LocalStorageManager.serializeFavorites(original)
        val deserialized = LocalStorageManager.parseFavorites(serialized)

        assertEquals(3, deserialized.size)
        assertTrue(deserialized.contains("v_001"))
        assertTrue(deserialized.contains("k_002"))
        assertTrue(deserialized.contains("u_1700000000_abc123"))
    }

    @Test
    fun serialization_userSentences_roundTrip() {
        val original = listOf(
            UserSentence(
                id = "u_10001_abc",
                categoryId = "my_values",
                text = "I value kindness and honesty.",
                createdAt = 1000L
            ),
            UserSentence(
                id = "u_10002_def",
                categoryId = "hard_days",
                text = "I can take this one breath at a time.",
                createdAt = 2000L
            )
        )
        val serialized = LocalStorageManager.serializeUserSentences(original)
        val deserialized = LocalStorageManager.parseUserSentences(serialized)

        assertEquals(2, deserialized.size)
        assertEquals("u_10001_abc", deserialized[0].id)
        assertEquals("my_values", deserialized[0].categoryId)
        assertEquals("I value kindness and honesty.", deserialized[0].text)
        assertEquals(1000L, deserialized[0].createdAt)

        assertEquals("u_10002_def", deserialized[1].id)
        assertEquals("hard_days", deserialized[1].categoryId)
        assertEquals("I can take this one breath at a time.", deserialized[1].text)
        assertEquals(2000L, deserialized[1].createdAt)
    }

    @Test
    fun serialization_sessionLog_strictlyNoUserText() {
        val original = listOf(
            SessionLogEntry(
                date = "2026-11-07",
                categoryId = "kind_to_myself",
                sentenceIds = listOf("k_001", "k_002")
            )
        )
        val serialized = LocalStorageManager.serializeSessionLog(original)
        assertFalse("Session log must never contain answer text", serialized.contains("answer"))

        val deserialized = LocalStorageManager.parseSessionLog(serialized)
        assertEquals(1, deserialized.size)
        assertEquals("2026-11-07", deserialized[0].date)
        assertEquals("kind_to_myself", deserialized[0].categoryId)
        assertEquals(listOf("k_001", "k_002"), deserialized[0].sentenceIds)
    }

    @Test
    fun serialization_openedCategories_roundTrip() {
        val original = linkedSetOf("my_values", "kind_to_myself")
        val serialized = LocalStorageManager.serializeOpenedCategories(original)
        val deserialized = LocalStorageManager.parseOpenedCategories(serialized)

        assertEquals(2, deserialized.size)
        assertTrue(deserialized.contains("my_values"))
        assertTrue(deserialized.contains("kind_to_myself"))
    }

    // ------------------------------------------------------------------------
    // 2. UserSentence ID Prefix and 200-Character Limit Validation
    // ------------------------------------------------------------------------

    @Test
    fun userSentence_prefixGenerationAndEnforcement() {
        // Automatically generated ID begins with u_
        val generatedId = UserSentence.generateId()
        assertTrue("Generated sentence ID must start with 'u_'", generatedId.startsWith("u_"))

        // Valid prefix succeeds
        val validSentence = UserSentence(id = "u_12345", categoryId = "my_values", text = "Valid text")
        assertEquals("u_12345", validSentence.id)

        // Invalid prefix throws IllegalArgumentException
        try {
            UserSentence(id = "invalid_12345", categoryId = "my_values", text = "Valid text")
            fail("Expected IllegalArgumentException when ID lacks 'u_' prefix")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("prefix"))
        }
    }

    @Test
    fun userSentence_200CharLimitValidation_saveUserSentence() {
        // Exactly 200 characters succeeds
        val exactly200Chars = "a".repeat(200)
        val saved = storageManager.saveUserSentence("my_values", exactly200Chars)
        assertNotNull(saved)
        assertTrue(saved.id.startsWith("u_"))
        assertEquals(200, saved.text.length)
        assertEquals(1, storageManager.getUserSentences().size)

        // 201 characters fails
        val exceeds200Chars = "a".repeat(201)
        try {
            storageManager.saveUserSentence("my_values", exceeds200Chars)
            fail("Expected IllegalArgumentException for text exceeding 200 characters")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("200"))
        }

        // Blank text fails
        try {
            storageManager.saveUserSentence("my_values", "   ")
            fail("Expected IllegalArgumentException for blank text")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("blank"))
        }
    }

    @Test
    fun userSentence_updateAndValidation() {
        val created = storageManager.saveUserSentence("my_values", "Original text")
        assertEquals("Original text", created.text)

        // Valid update <= 200 characters
        val updateSuccess = storageManager.updateUserSentence(created.id, "Updated text")
        assertTrue(updateSuccess)
        val updatedSentence = storageManager.getUserSentences().first { it.id == created.id }
        assertEquals("Updated text", updatedSentence.text)

        // Exceeding 200 chars returns false and does not modify
        val tooLong = "x".repeat(201)
        val updateFailedTooLong = storageManager.updateUserSentence(created.id, tooLong)
        assertFalse(updateFailedTooLong)
        assertEquals("Updated text", storageManager.getUserSentences().first { it.id == created.id }.text)

        // Blank update returns false
        val updateFailedBlank = storageManager.updateUserSentence(created.id, "   ")
        assertFalse(updateFailedBlank)

        // Non-existent ID returns false
        val nonExistentUpdate = storageManager.updateUserSentence("u_nonexistent", "Hello")
        assertFalse(nonExistentUpdate)
    }

    @Test
    fun userSentence_deletionAndCategoryFiltering() {
        val s1 = storageManager.saveUserSentence("my_values", "Value sentence")
        val s2 = storageManager.saveUserSentence("kind_to_myself", "Kindness sentence")

        // Filter by category
        val valuesSentences = storageManager.getUserSentencesForCategory("my_values")
        assertEquals(1, valuesSentences.size)
        assertEquals(s1.id, valuesSentences[0].id)

        val kindSentences = storageManager.getUserSentencesForCategory("kind_to_myself")
        assertEquals(1, kindSentences.size)
        assertEquals(s2.id, kindSentences[0].id)

        // Favorite s1 then delete s1 -> should be removed from favorites as well
        storageManager.toggleFavorite(s1.id)
        assertTrue(storageManager.isFavorite(s1.id))

        val deleteSuccess = storageManager.deleteUserSentence(s1.id)
        assertTrue(deleteSuccess)
        assertEquals(1, storageManager.getUserSentences().size)
        assertFalse(storageManager.isFavorite(s1.id))

        // Deleting already deleted sentence returns false
        assertFalse(storageManager.deleteUserSentence(s1.id))
    }

    // ------------------------------------------------------------------------
    // 3. 60-Item Session Log Limit & FIFO Pruning
    // ------------------------------------------------------------------------

    @Test
    fun sessionLog_60ItemLimit_fifoPruning() {
        // Add 65 session logs
        for (i in 1..65) {
            val date = String.format("2026-11-%02d", ((i - 1) % 28) + 1)
            storageManager.recordSession(
                categoryId = "cat_$i",
                sentenceIds = listOf("s_${i}_1", "s_${i}_2"),
                localDate = date
            )
        }

        val log = storageManager.getSessionLog()
        assertEquals("Session log must be capped at 60 entries", 60, log.size)

        // Newest entry must be at index 0 (cat_65)
        assertEquals("cat_65", log[0].categoryId)
        assertEquals("cat_64", log[1].categoryId)

        // Oldest retained entry at index 59 must be cat_6 (cat_1 to cat_5 pruned FIFO)
        assertEquals("cat_6", log[59].categoryId)

        // Verify cat_1 through cat_5 are absent
        val recordedCategoryIds = log.map { it.categoryId }.toSet()
        assertFalse(recordedCategoryIds.contains("cat_1"))
        assertFalse(recordedCategoryIds.contains("cat_2"))
        assertFalse(recordedCategoryIds.contains("cat_3"))
        assertFalse(recordedCategoryIds.contains("cat_4"))
        assertFalse(recordedCategoryIds.contains("cat_5"))
        assertTrue(recordedCategoryIds.contains("cat_6"))
    }

    @Test
    fun sessionLog_hasCompletedSessionToday() {
        assertFalse(storageManager.hasCompletedSessionToday("2026-11-07"))

        storageManager.recordSession(
            categoryId = "my_values",
            sentenceIds = listOf("v_001"),
            localDate = "2026-11-07"
        )

        assertTrue(storageManager.hasCompletedSessionToday("2026-11-07"))
        assertFalse(storageManager.hasCompletedSessionToday("2026-11-08"))
    }

    // ------------------------------------------------------------------------
    // 4. Onboarding, Level Adaptation, Not Yet Streak
    // ------------------------------------------------------------------------

    @Test
    fun onboarding_completed_updatesSettingsAndInitialLevels() {
        val targetCategories = listOf("my_values", "kind_to_myself", "hard_days")
        storageManager.setOnboardingCompleted(
            answer = "I believe it",
            startingLevel = Level.MEDIUM,
            categoryIds = targetCategories
        )

        val settings = storageManager.getSettings()
        assertEquals("I believe it", settings.onboardingAnswer)
        assertTrue(settings.howItWorksSeen)

        assertEquals(Level.MEDIUM, storageManager.getCategoryLevel("my_values"))
        assertEquals(Level.MEDIUM, storageManager.getCategoryLevel("kind_to_myself"))
        assertEquals(Level.MEDIUM, storageManager.getCategoryLevel("hard_days"))
        // Unset category falls back to GENTLE default
        assertEquals(Level.GENTLE, storageManager.getCategoryLevel("other_cat"))
    }

    @Test
    fun levelOperations_andNotYetStreak() {
        assertEquals(Level.GENTLE, storageManager.getCategoryLevel("my_values"))
        storageManager.setCategoryLevel("my_values", Level.STRONG)
        assertEquals(Level.STRONG, storageManager.getCategoryLevel("my_values"))

        assertEquals(0, storageManager.getNotYetStreak("my_values"))
        assertEquals(1, storageManager.incrementNotYetStreak("my_values"))
        assertEquals(2, storageManager.incrementNotYetStreak("my_values"))
        assertEquals(2, storageManager.getNotYetStreak("my_values"))

        storageManager.resetNotYetStreak("my_values")
        assertEquals(0, storageManager.getNotYetStreak("my_values"))
    }

    // ------------------------------------------------------------------------
    // 5. Favorites & Opened Categories
    // ------------------------------------------------------------------------

    @Test
    fun favorites_toggleOperations() {
        assertFalse(storageManager.isFavorite("s_01"))

        // Add
        val added = storageManager.toggleFavorite("s_01")
        assertTrue(added)
        assertTrue(storageManager.isFavorite("s_01"))
        assertTrue(storageManager.getFavorites().contains("s_01"))

        // Remove
        val removed = storageManager.toggleFavorite("s_01")
        assertFalse(removed)
        assertFalse(storageManager.isFavorite("s_01"))
        assertFalse(storageManager.getFavorites().contains("s_01"))
    }

    @Test
    fun openedCategories_markAndCheck() {
        assertFalse(storageManager.isCategoryOpened("my_values"))

        storageManager.markCategoryOpened("my_values")
        assertTrue(storageManager.isCategoryOpened("my_values"))

        // Marking again does not duplicate or cause errors
        storageManager.markCategoryOpened("my_values")
        assertTrue(storageManager.isCategoryOpened("my_values"))
    }

    // ------------------------------------------------------------------------
    // 6. Delete All Data Reset
    // ------------------------------------------------------------------------

    @Test
    fun deleteAllData_completelyClearsPreferencesAndResetsState() {
        // Populate state across all domains
        storageManager.setName("Jane")
        storageManager.setOnboardingCompleted("I believe it", Level.STRONG)
        storageManager.setCategoryLevel("my_values", Level.STRONG)
        storageManager.incrementNotYetStreak("my_values")
        storageManager.toggleFavorite("aff_01")
        storageManager.saveUserSentence("my_values", "My special affirmation")
        storageManager.recordSession("my_values", listOf("aff_01"), "2026-11-07")
        storageManager.markCategoryOpened("my_values")

        // Verify state is non-empty before reset
        assertEquals("Jane", storageManager.getSettings().name)
        assertEquals(Level.STRONG, storageManager.getCategoryLevel("my_values"))
        assertEquals(1, storageManager.getNotYetStreak("my_values"))
        assertEquals(1, storageManager.getFavorites().size)
        assertEquals(1, storageManager.getUserSentences().size)
        assertEquals(1, storageManager.getSessionLog().size)
        assertTrue(storageManager.isCategoryOpened("my_values"))

        // Execute complete reset
        storageManager.deleteAllData()

        // Verify every domain is reset to empty/default
        val resetSettings = storageManager.getSettings()
        assertNull(resetSettings.name)
        assertNull(resetSettings.onboardingAnswer)
        assertFalse(resetSettings.howItWorksSeen)

        assertEquals(Level.GENTLE, storageManager.getCategoryLevel("my_values"))
        assertEquals(0, storageManager.getNotYetStreak("my_values"))
        assertTrue(storageManager.getFavorites().isEmpty())
        assertTrue(storageManager.getUserSentences().isEmpty())
        assertTrue(storageManager.getSessionLog().isEmpty())
        assertFalse(storageManager.isCategoryOpened("my_values"))

        // Verify StateFlow emissions reflect reset
        assertEquals(UserSettings(), storageManager.settingsFlow.value)
        assertTrue(storageManager.categoryLevelsFlow.value.isEmpty())
        assertTrue(storageManager.notYetStreakFlow.value.isEmpty())
        assertTrue(storageManager.favoritesFlow.value.isEmpty())
        assertTrue(storageManager.userSentencesFlow.value.isEmpty())
        assertTrue(storageManager.sessionLogFlow.value.isEmpty())
        assertTrue(storageManager.openedCategoriesFlow.value.isEmpty())

        // Verify underlying SharedPreferences is completely cleared
        assertTrue(fakePrefs.getAll().isEmpty())
    }

    // ------------------------------------------------------------------------
    // In-Memory Fake SharedPreferences for JVM Unit Testing
    // ------------------------------------------------------------------------

    internal class FakeSharedPreferences : SharedPreferences {
        private val data = ConcurrentHashMap<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(data)

        override fun getString(key: String?, defValue: String?): String? {
            val v = data[key]
            return if (v is String) v else defValue
        }

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            val v = data[key]
            return if (v is Set<*>) (v as Set<String>).toMutableSet() else defValues
        }

        override fun getInt(key: String?, defValue: Int): Int {
            val v = data[key]
            return if (v is Number) v.toInt() else defValue
        }

        override fun getLong(key: String?, defValue: Long): Long {
            val v = data[key]
            return if (v is Number) v.toLong() else defValue
        }

        override fun getFloat(key: String?, defValue: Float): Float {
            val v = data[key]
            return if (v is Number) v.toFloat() else defValue
        }

        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            val v = data[key]
            return if (v is Boolean) v else defValue
        }

        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor(this)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        internal class FakeEditor(private val prefs: FakeSharedPreferences) : SharedPreferences.Editor {
            private val temp = HashMap<String, Any?>()
            private var clearFlag = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) temp[key] = values?.toSet()
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) temp[key] = this
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearFlag = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                synchronized(prefs.data) {
                    if (clearFlag) {
                        prefs.data.clear()
                    }
                    for ((k, v) in temp) {
                        if (v === this) {
                            prefs.data.remove(k)
                        } else if (v == null) {
                            prefs.data.remove(k)
                        } else {
                            prefs.data[k] = v
                        }
                    }
                }
            }
        }
    }
}
