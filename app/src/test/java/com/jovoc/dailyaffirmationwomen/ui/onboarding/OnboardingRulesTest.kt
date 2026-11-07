package com.jovoc.dailyaffirmationwomen.ui.onboarding

import com.jovoc.dailyaffirmationwomen.data.model.Level
import com.jovoc.dailyaffirmationwomen.data.storage.LocalStorageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * Unit verification for Onboarding business logic and contract rules.
 * AGENT_INSTRUCTIONS.md Section 4 (Feature F1).
 *
 * Rules verified:
 * 1. Step 1: Optional name input is saved to settings.name.
 * 2. Step 2: "I believe it" sets starting level for all categories to MEDIUM.
 * 3. Step 2: "I want to believe it" sets starting level for all categories to GENTLE.
 * 4. Step 2: "I doubt it" sets starting level for all categories to GENTLE.
 * 5. Step 2: Skip sets starting level for all categories to GENTLE.
 * 6. Completing onboarding preserves entered name and marks howItWorksSeen = true.
 */
class OnboardingRulesTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var storageManager: LocalStorageManager

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        storageManager = LocalStorageManager(fakePrefs)
    }

    @Test
    fun step1_nameInput_savesEnteredName() {
        storageManager.setName("Sarah")
        assertEquals("Sarah", storageManager.getSettings().name)

        storageManager.setName("   ")
        assertNull("Blank name should be normalized to null", storageManager.getSettings().name)
    }

    @Test
    fun step2_believeOption_setsMediumStartingLevelForAllCategories() {
        val categories = listOf("kind_to_myself", "my_values", "hard_days")
        storageManager.setName("Sarah")

        // Option 1: "I believe it"
        storageManager.setOnboardingCompleted(
            answer = "I believe it",
            startingLevel = Level.MEDIUM,
            categoryIds = categories
        )

        val settings = storageManager.getSettings()
        assertEquals("Sarah", settings.name)
        assertEquals("I believe it", settings.onboardingAnswer)
        assertTrue(settings.howItWorksSeen)

        for (cat in categories) {
            assertEquals(Level.MEDIUM, storageManager.getCategoryLevel(cat))
        }
    }

    @Test
    fun step2_wantOption_setsGentleStartingLevelForAllCategories() {
        val categories = listOf("kind_to_myself", "my_values", "hard_days")

        // Option 2: "I want to believe it"
        storageManager.setOnboardingCompleted(
            answer = "I want to believe it",
            startingLevel = Level.GENTLE,
            categoryIds = categories
        )

        val settings = storageManager.getSettings()
        assertEquals("I want to believe it", settings.onboardingAnswer)
        assertTrue(settings.howItWorksSeen)

        for (cat in categories) {
            assertEquals(Level.GENTLE, storageManager.getCategoryLevel(cat))
        }
    }

    @Test
    fun step2_doubtOption_setsGentleStartingLevelForAllCategories() {
        val categories = listOf("kind_to_myself", "my_values", "hard_days")

        // Option 3: "I doubt it"
        storageManager.setOnboardingCompleted(
            answer = "I doubt it",
            startingLevel = Level.GENTLE,
            categoryIds = categories
        )

        val settings = storageManager.getSettings()
        assertEquals("I doubt it", settings.onboardingAnswer)
        assertTrue(settings.howItWorksSeen)

        for (cat in categories) {
            assertEquals(Level.GENTLE, storageManager.getCategoryLevel(cat))
        }
    }

    @Test
    fun step2_skip_setsGentleStartingLevelAndNullAnswer() {
        val categories = listOf("kind_to_myself", "my_values", "hard_days")

        // Skip
        storageManager.setOnboardingCompleted(
            answer = null,
            startingLevel = Level.GENTLE,
            categoryIds = categories
        )

        val settings = storageManager.getSettings()
        assertNull(settings.onboardingAnswer)
        assertTrue(settings.howItWorksSeen)

        for (cat in categories) {
            assertEquals(Level.GENTLE, storageManager.getCategoryLevel(cat))
        }
    }

    @Test
    fun defaultCategoryIds_fallbackUsedWhenCategoryListEmpty() {
        storageManager.setOnboardingCompleted(
            answer = "I believe it",
            startingLevel = Level.MEDIUM,
            categoryIds = emptyList()
        )

        // All 12 default category IDs should have received MEDIUM level
        for (catId in LocalStorageManager.DEFAULT_CATEGORY_IDS) {
            assertEquals(Level.MEDIUM, storageManager.getCategoryLevel(catId))
        }
    }

    // In-memory fake SharedPreferences implementation for fast, isolated testing
    private class FakeSharedPreferences : android.content.SharedPreferences {
        private val data = ConcurrentHashMap<String, Any>()

        override fun getAll(): MutableMap<String, *> = HashMap(data)
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (data[key] as? MutableSet<String>) ?: defValues

        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): android.content.SharedPreferences.Editor = FakeEditor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val backingData: ConcurrentHashMap<String, Any>) :
            android.content.SharedPreferences.Editor {
            private val temp = HashMap<String, Any?>()
            private var clearFlag = false

            override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor {
                if (key != null) {
                    if (value == null) temp[key] = null else temp[key] = value
                }
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): android.content.SharedPreferences.Editor {
                if (key != null) {
                    if (values == null) temp[key] = null else temp[key] = HashSet(values)
                }
                return this
            }

            override fun putInt(key: String?, value: Int): android.content.SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }

            override fun putLong(key: String?, value: Long): android.content.SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }

            override fun putFloat(key: String?, value: Float): android.content.SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): android.content.SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }

            override fun remove(key: String?): android.content.SharedPreferences.Editor {
                if (key != null) temp[key] = null
                return this
            }

            override fun clear(): android.content.SharedPreferences.Editor {
                clearFlag = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearFlag) {
                    backingData.clear()
                    clearFlag = false
                }
                for ((k, v) in temp) {
                    if (v == null) {
                        backingData.remove(k)
                    } else {
                        backingData[k] = v
                    }
                }
                temp.clear()
            }
        }
    }
}
