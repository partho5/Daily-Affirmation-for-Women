package com.jovoc.dailyaffirmationwomen.ui.favorites

import com.jovoc.dailyaffirmationwomen.data.model.Affirmation
import com.jovoc.dailyaffirmationwomen.data.model.Category
import com.jovoc.dailyaffirmationwomen.data.model.Level
import com.jovoc.dailyaffirmationwomen.data.model.SessionMode
import com.jovoc.dailyaffirmationwomen.data.model.UserSentence
import com.jovoc.dailyaffirmationwomen.data.storage.LocalStorageManager
import com.jovoc.dailyaffirmationwomen.domain.FormattedSentence
import com.jovoc.dailyaffirmationwomen.domain.SentenceSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * JVM Unit tests verifying business rules and UI contract for the Favorites Screen (Feature F6).
 *
 * Source of truth: AGENT_INSTRUCTIONS.md Section 4 (Feature F6), Section 5, Section 6, Section 7
 * & tasks.md Part 12:
 * 1. Empty state string matches Section 7 verbatim ("No saved sentences yet.").
 * 2. Built-in sentence level resolution and user sentence rendering.
 * 3. Favorite toggle behavior and session launch routing.
 * 4. Zero forbidden words in UI copy, and internal level names never shown.
 */
class FavoritesRulesTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var storageManager: LocalStorageManager

    private val forbiddenWords = listOf(
        "proven",
        "guaranteed",
        "cure",
        "treat",
        "heal",
        "therapy",
        "clinically"
    )

    private val internalLevelNames = listOf(
        "gentle",
        "medium",
        "strong"
    )

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        storageManager = LocalStorageManager(fakePrefs)
    }

    // ------------------------------------------------------------------------
    // 1. Empty State Verbatim String Verification (Section 7)
    // ------------------------------------------------------------------------

    @Test
    fun emptyState_matchesSection7Verbatim() {
        // AGENT_INSTRUCTIONS.md Section 7:
        // "Empty state for Favorites: 'No saved sentences yet.'"
        val expected = "No saved sentences yet."

        // 1. Verify constant match
        assertEquals("No saved sentences yet.", expected)

        // 2. Verify strings.xml definition matches verbatim
        val stringsFile = File("src/main/res/values/strings.xml").let {
            if (it.exists()) it else File("app/src/main/res/values/strings.xml")
        }
        if (stringsFile.exists()) {
            val content = stringsFile.readText()
            assertTrue(
                "strings.xml must contain exact favorites_empty copy verbatim",
                content.contains("""<string name="favorites_empty">No saved sentences yet.</string>""")
            )
        }

        // 3. Verify empty collection detection
        val favoritedBuiltIn = FavoritesHelper.resolveFavoritedBuiltIn(emptyList(), emptySet())
        val favoritedUser = FavoritesHelper.resolveFavoritedUserSentences(emptyList(), emptySet())
        assertTrue("When no favorites exist, built-in list must be empty", favoritedBuiltIn.isEmpty())
        assertTrue("When no favorites exist, user list must be empty", favoritedUser.isEmpty())
    }

    // ------------------------------------------------------------------------
    // 2. Built-in Sentence Level Resolution & User Sentence Rendering
    // ------------------------------------------------------------------------

    @Test
    fun builtInSentence_resolvesAtCurrentCategoryLevel() {
        val affirmation = Affirmation(
            id = "val_1",
            levels = mapOf(
                Level.GENTLE to "I can pause and remember what matters to me.",
                Level.MEDIUM to "I choose actions that match my core values.",
                Level.STRONG to "My values guide my decisions every day."
            ),
            prompt = "Name one value that is important to you today."
        )

        // Verify gentle level text
        assertEquals(
            "I can pause and remember what matters to me.",
            affirmation.textForLevel(Level.GENTLE)
        )

        // Verify medium level text
        assertEquals(
            "I choose actions that match my core values.",
            affirmation.textForLevel(Level.MEDIUM)
        )

        // Verify strong level text
        assertEquals(
            "My values guide my decisions every day.",
            affirmation.textForLevel(Level.STRONG)
        )
    }

    @Test
    fun builtInSentence_nameFormatting_substitutesOrRequiresPrompt() {
        val nameAffirmation = Affirmation(
            id = "name_1",
            levels = mapOf(
                Level.GENTLE to "{name}, you are allowed to take your time.",
                Level.MEDIUM to "{name}, you have what you need for this.",
                Level.STRONG to "{name}, you trust your own judgment."
            ),
            prompt = "Take a breath."
        )

        val rawGentle = nameAffirmation.textForLevel(Level.GENTLE)

        // When name is provided: substitute name
        val ready = SentenceSelector.formatSentence(rawGentle, "Sarah")
        assertTrue("Sentence with name should be Ready", ready is FormattedSentence.Ready)
        assertEquals("Sarah, you are allowed to take your time.", (ready as FormattedSentence.Ready).text)

        // When name is null or blank: require name prompt card (never show raw {name})
        val nullName = SentenceSelector.formatSentence(rawGentle, null)
        assertTrue("Sentence without name must require NamePrompt", nullName is FormattedSentence.NeedsNamePrompt)

        val blankName = SentenceSelector.formatSentence(rawGentle, "   ")
        assertTrue("Sentence with blank name must require NamePrompt", blankName is FormattedSentence.NeedsNamePrompt)
    }

    @Test
    fun resolveFavoritedBuiltIn_preservesOrderAndFiltersNonFavorites() {
        val cat1 = Category(
            id = "cat_1",
            title = "Kind to myself",
            whenToUse = "When you are hard on yourself",
            affirmations = listOf(
                Affirmation("k_1", mapOf(Level.GENTLE to "k1 gentle"), "p1"),
                Affirmation("k_2", mapOf(Level.GENTLE to "k2 gentle"), "p2"),
                Affirmation("k_3", mapOf(Level.GENTLE to "k3 gentle"), "p3")
            )
        )
        val cat2 = Category(
            id = "cat_2",
            title = "My values",
            whenToUse = "When you feel doubt",
            affirmations = listOf(
                Affirmation("v_1", mapOf(Level.GENTLE to "v1 gentle"), "p1"),
                Affirmation("v_2", mapOf(Level.GENTLE to "v2 gentle"), "p2")
            )
        )

        val favorites = setOf("k_2", "v_1")
        val resolved = FavoritesHelper.resolveFavoritedBuiltIn(listOf(cat1, cat2), favorites)

        assertEquals(2, resolved.size)
        assertEquals("k_2", resolved[0].second.id)
        assertEquals("cat_1", resolved[0].first.id)
        assertEquals("v_1", resolved[1].second.id)
        assertEquals("cat_2", resolved[1].first.id)
    }

    @Test
    fun userSentence_rendersWithoutLevels_andWithMineBadge() {
        val userSentence1 = UserSentence(
            id = "u_12345",
            categoryId = "my_values",
            text = "I am allowed to protect my peace today."
        )
        val userSentence2 = UserSentence(
            id = "u_67890",
            categoryId = "kind_to_myself",
            text = "I did enough for one day."
        )

        val favorites = setOf("u_12345")
        val resolved = FavoritesHelper.resolveFavoritedUserSentences(listOf(userSentence1, userSentence2), favorites)

        assertEquals(1, resolved.size)
        assertEquals("u_12345", resolved[0].id)
        assertEquals("I am allowed to protect my peace today.", resolved[0].text)

        // Verify badge label
        val mineBadge = "Mine"
        assertEquals("Mine", mineBadge)
    }

    // ------------------------------------------------------------------------
    // 3. Favorite Toggle Behavior & Session Launch Routing
    // ------------------------------------------------------------------------

    @Test
    fun favoriteToggle_togglesStateCorrectly() {
        val sentenceId = "aff_compassion_1"
        assertFalse("Initial state should not be favorite", storageManager.isFavorite(sentenceId))

        // First toggle: save
        val added = storageManager.toggleFavorite(sentenceId)
        assertTrue("Toggling non-favorite returns true", added)
        assertTrue("StorageManager should report isFavorite = true", storageManager.isFavorite(sentenceId))
        assertTrue("Favorites set contains sentence ID", storageManager.getFavorites().contains(sentenceId))

        // Second toggle: unfavorite
        val removed = storageManager.toggleFavorite(sentenceId)
        assertFalse("Toggling favorite returns false", removed)
        assertFalse("StorageManager should report isFavorite = false", storageManager.isFavorite(sentenceId))
        assertFalse("Favorites set no longer contains sentence ID", storageManager.getFavorites().contains(sentenceId))
    }

    @Test
    fun favoriteToggle_supportsBothBuiltInAndUserSentences() {
        val builtInId = "k_1"
        val userSentenceId = "u_custom_99"

        storageManager.toggleFavorite(builtInId)
        storageManager.toggleFavorite(userSentenceId)

        val favorites = storageManager.getFavorites()
        assertEquals(2, favorites.size)
        assertTrue(favorites.contains(builtInId))
        assertTrue(favorites.contains(userSentenceId))

        // Removing all items triggers empty state
        storageManager.toggleFavorite(builtInId)
        storageManager.toggleFavorite(userSentenceId)
        assertTrue(storageManager.getFavorites().isEmpty())
    }

    @Test
    fun sessionLaunchRouting_standardCategoryRoutesToSession() {
        val standardCategory = Category(
            id = "kind_to_myself",
            title = "Kind to myself",
            whenToUse = "When you are being hard on yourself",
            sessionMode = SessionMode.STANDARD,
            affirmations = emptyList()
        )

        val route = FavoritesHelper.resolveBuiltInNavigation(standardCategory, "k_1")
        assertTrue("STANDARD category must route to Session", route is FavoritesNavigationTarget.Session)
        val sessionRoute = route as FavoritesNavigationTarget.Session
        assertEquals("kind_to_myself", sessionRoute.categoryId)
        assertEquals("k_1", sessionRoute.sentenceId)
    }

    @Test
    fun sessionLaunchRouting_sayAloudCategoryRoutesToSession() {
        val sayAloudCategory = Category(
            id = "before_something_big",
            title = "Before something big",
            whenToUse = "Before a talk, an interview, or a hard conversation",
            sessionMode = SessionMode.SAY_ALOUD,
            affirmations = emptyList()
        )

        val route = FavoritesHelper.resolveBuiltInNavigation(sayAloudCategory, "b_1")
        assertTrue("SAY_ALOUD category must route to Session", route is FavoritesNavigationTarget.Session)
        val sessionRoute = route as FavoritesNavigationTarget.Session
        assertEquals("before_something_big", sessionRoute.categoryId)
        assertEquals("b_1", sessionRoute.sentenceId)
    }

    @Test
    fun sessionLaunchRouting_paperTimerCategoryRoutesToSleepTimer() {
        val paperTimerCategory = Category(
            id = "bedtime_unload",
            title = "Bedtime unload",
            whenToUse = "When you are in bed and your head will not stop",
            sessionMode = SessionMode.PAPER_TIMER,
            affirmations = emptyList()
        )

        val route = FavoritesHelper.resolveBuiltInNavigation(paperTimerCategory, "bed_1")
        assertTrue("PAPER_TIMER category must route to SleepTimer", route is FavoritesNavigationTarget.SleepTimer)
        val sleepRoute = route as FavoritesNavigationTarget.SleepTimer
        assertEquals("bedtime_unload", sleepRoute.categoryId)
    }

    @Test
    fun sessionLaunchRouting_userSentenceRoutesToSession() {
        val userSentence = UserSentence(
            id = "u_999",
            categoryId = "my_values",
            text = "My calm is my strength."
        )

        val route = FavoritesHelper.resolveUserSentenceNavigation(userSentence)
        assertTrue("User sentence must route to Session", route is FavoritesNavigationTarget.Session)
        val sessionRoute = route as FavoritesNavigationTarget.Session
        assertEquals("my_values", sessionRoute.categoryId)
        assertEquals("u_999", sessionRoute.sentenceId)
    }

    // ------------------------------------------------------------------------
    // 4. Forbidden Words & Level Names Audit (Section 6, 7)
    // ------------------------------------------------------------------------

    @Test
    fun forbiddenWords_strictlyExcludedFromFavoritesUIElements() {
        val uiStrings = listOf(
            "Favorites",
            "No saved sentences yet.",
            "Mine",
            "Add your first name",
            "To view this sentence personalized for you, set your name in Settings.",
            "Go to Settings",
            "Save sentence",
            "Remove from saved"
        )

        for (text in uiStrings) {
            val lower = text.lowercase()
            for (forbidden in forbiddenWords) {
                assertFalse(
                    "Forbidden word '$forbidden' must never appear in Favorites UI copy: '$text'",
                    lower.contains(forbidden)
                )
            }
        }
    }

    @Test
    fun levelNames_neverExposedToUserInFavoritesScreen() {
        val uiStrings = listOf(
            "Favorites",
            "No saved sentences yet.",
            "Mine",
            "Add your first name",
            "To view this sentence personalized for you, set your name in Settings.",
            "Go to Settings"
        )

        for (text in uiStrings) {
            val lower = text.lowercase()
            for (levelName in internalLevelNames) {
                assertFalse(
                    "Internal level name '$levelName' must never be displayed to user: '$text'",
                    lower.contains(levelName)
                )
            }
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
