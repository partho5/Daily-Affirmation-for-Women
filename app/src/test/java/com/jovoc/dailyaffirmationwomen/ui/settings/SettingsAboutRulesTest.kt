package com.jovoc.dailyaffirmationwomen.ui.settings

import android.content.SharedPreferences
import com.jovoc.dailyaffirmationwomen.R
import com.jovoc.dailyaffirmationwomen.data.model.Level
import com.jovoc.dailyaffirmationwomen.data.storage.LocalStorageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * JVM Unit tests verifying business rules, UI contract, and privacy boundaries for Settings & About Screens (Feature F7).
 *
 * Source of truth: AGENT_INSTRUCTIONS.md Section 3.4, Section 4 (F7), Section 5, Section 6, Section 7 & tasks.md Part 13:
 * 1. Verifies About disclaimer character-by-character.
 * 2. Verifies "Delete all my data" wipes all storage keys and resets state.
 * 3. Verifies 6 reading rules use exact copy.
 * 4. Verifies zero forbidden words outside of the verbatim disclaimer.
 * 5. Strict prohibition: No arbitrary phone numbers or website links on About screen.
 * 6. Internal level names ("gentle", "medium", "strong") never exposed in UI copy.
 */
class SettingsAboutRulesTest {

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
    // 1. Verifies About Disclaimer Character-by-Character (Section 4 F7 & 5)
    // ------------------------------------------------------------------------

    @Test
    fun aboutDisclaimer_matchesVerbatimCharacterByCharacter() {
        // AGENT_INSTRUCTIONS.md Section 4 (F7):
        // "About screen with this exact text: This app is for everyday well-being. It is not medical care or therapy. If you are in distress, please contact a local health professional or emergency services."
        val expectedDisclaimer =
            "This app is for everyday well-being. It is not medical care or therapy. If you are in distress, please contact a local health professional or emergency services."

        val xmlContent = loadStringsXml()
        val rawDisclaimer = extractStringResource(xmlContent, "about_disclaimer")
        val actualDisclaimer = unescapeXmlString(rawDisclaimer)

        assertEquals("Character length must match exactly", expectedDisclaimer.length, actualDisclaimer.length)

        for (i in expectedDisclaimer.indices) {
            assertEquals(
                "Character mismatch at index $i: expected '${expectedDisclaimer[i]}' but got '${actualDisclaimer[i]}'",
                expectedDisclaimer[i],
                actualDisclaimer[i]
            )
        }
        assertEquals("About disclaimer must match verbatim", expectedDisclaimer, actualDisclaimer)
    }

    @Test
    fun aboutScreen_containsRequiredDisclaimerAndDeveloperComment() {
        val aboutScreenFile = findFile("app/src/main/java/com/jovoc/dailyaffirmationwomen/ui/settings/AboutScreen.kt")
        assertTrue("AboutScreen.kt file must exist", aboutScreenFile.exists())
        val code = aboutScreenFile.readText()

        // Verify reference to string resource
        assertTrue(
            "AboutScreen must bind to R.string.about_disclaimer",
            code.contains("R.string.about_disclaimer")
        )

        // Verify verbatim developer TODO comment
        assertTrue(
            "AboutScreen must contain developer helpline TODO comment",
            code.contains("// TODO(developer): developer will add external helpline numbers and links")
        )
    }

    @Test
    fun aboutScreen_strictProhibition_noArbitraryPhoneNumbersOrLinks() {
        val aboutScreenFile = findFile("app/src/main/java/com/jovoc/dailyaffirmationwomen/ui/settings/AboutScreen.kt")
        val code = aboutScreenFile.readText()

        // Strict prohibition: Do NOT add arbitrary phone numbers or website links
        assertFalse(
            "AboutScreen must not contain http links",
            code.contains("http://", ignoreCase = true)
        )
        assertFalse(
            "AboutScreen must not contain https links",
            code.contains("https://", ignoreCase = true)
        )
        assertFalse(
            "AboutScreen must not contain www links",
            code.contains("www.", ignoreCase = true)
        )
        assertFalse(
            "AboutScreen must not contain tel: links",
            code.contains("tel:", ignoreCase = true)
        )

        // Regex check for phone numbers (e.g. 1-800-xxx-xxxx or +1 xxx xxx xxxx)
        val phoneRegex = Regex("""(\+?\d{1,3}[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}""")
        assertFalse(
            "AboutScreen must not contain hardcoded phone numbers",
            phoneRegex.containsMatchIn(code)
        )
    }

    // ------------------------------------------------------------------------
    // 2. Verifies "Delete all my data" Wipes All Storage Keys and Resets State
    // ------------------------------------------------------------------------

    @Test
    fun deleteAllData_wipesAllStorageKeysAndResetsFlows() {
        // 1. Populate all storage keys
        storageManager.setName("Jane")
        storageManager.setOnboardingCompleted(
            answer = "I believe it",
            startingLevel = Level.MEDIUM,
            categoryIds = listOf("kind_myself", "before_sleep")
        )
        storageManager.setCategoryLevel("kind_myself", Level.STRONG)
        storageManager.incrementNotYetStreak("kind_myself")
        storageManager.toggleFavorite("sent_1")
        storageManager.saveUserSentence("kind_myself", "Custom affirmation test sentence")
        storageManager.recordSession("kind_myself", listOf("sent_1"))
        storageManager.markCategoryOpened("kind_myself")

        // Verify storage is non-empty before delete
        assertEquals("Jane", storageManager.getSettings().name)
        assertTrue(storageManager.getSettings().howItWorksSeen)
        assertEquals(Level.STRONG, storageManager.getCategoryLevel("kind_myself"))
        assertEquals(1, storageManager.getNotYetStreak("kind_myself"))
        assertTrue(storageManager.isFavorite("sent_1"))
        assertEquals(1, storageManager.getUserSentences().size)
        assertEquals(1, storageManager.getSessionLog().size)
        assertTrue(storageManager.isCategoryOpened("kind_myself"))
        assertTrue("Preferences backing data must not be empty before delete", fakePrefs.getAll().isNotEmpty())

        // 2. Execute Delete All Data
        storageManager.deleteAllData()

        // 3. Verify all SharedPreferences keys are completely wiped
        assertTrue(
            "All SharedPreferences keys must be cleared after deleteAllData",
            fakePrefs.getAll().isEmpty()
        )

        // 4. Verify all reactive StateFlows are reset to initial empty defaults
        val resetSettings = storageManager.settingsFlow.value
        assertNull("Name must be reset to null", resetSettings.name)
        assertNull("Onboarding answer must be reset to null", resetSettings.onboardingAnswer)
        assertFalse("howItWorksSeen must be reset to false", resetSettings.howItWorksSeen)

        assertTrue("Category levels must be reset to empty", storageManager.categoryLevelsFlow.value.isEmpty())
        assertTrue("Not yet streak must be reset to empty", storageManager.notYetStreakFlow.value.isEmpty())
        assertTrue("Favorites must be reset to empty", storageManager.favoritesFlow.value.isEmpty())
        assertTrue("User sentences must be reset to empty", storageManager.userSentencesFlow.value.isEmpty())
        assertTrue("Session log must be reset to empty", storageManager.sessionLogFlow.value.isEmpty())
        assertTrue("Opened categories must be reset to empty", storageManager.openedCategoriesFlow.value.isEmpty())
    }

    // ------------------------------------------------------------------------
    // 3. Verifies Six Reading Rules Use Exact Copy (Section 3.4 & Section 5)
    // ------------------------------------------------------------------------

    @Test
    fun sixReadingRules_matchesExactCopy() {
        val expectedRules = listOf(
            // Rule 1: how_card_1
            "Read it slowly, once or twice. Say it out loud if you can.",
            // Rule 2: how_card_2
            "Ask yourself if it feels true. \"Not yet\" is a fine answer.",
            // Rule 3: rule_3
            "If it does not feel true, think of a time it was true and a time it was not.",
            // Rule 4: rule_4
            "Do the small step under the sentence. It matters more than the sentence.",
            // Rule 5: rule_5
            "Stop after one to three sentences. One session a day is enough.",
            // Rule 6: rule_6
            "If something makes you feel worse, stop. Try another category or take a break."
        )

        val expectedResourceKeys = listOf(
            "how_card_1",
            "how_card_2",
            "rule_3",
            "rule_4",
            "rule_5",
            "rule_6"
        )

        // Verify resource list in SettingsReadingRules has exactly 6 rules
        assertEquals("SettingsReadingRules must declare exactly 6 rules", 6, SettingsReadingRules.ruleResIds.size)
        assertEquals(R.string.how_card_1, SettingsReadingRules.ruleResIds[0])
        assertEquals(R.string.how_card_2, SettingsReadingRules.ruleResIds[1])
        assertEquals(R.string.rule_3, SettingsReadingRules.ruleResIds[2])
        assertEquals(R.string.rule_4, SettingsReadingRules.ruleResIds[3])
        assertEquals(R.string.rule_5, SettingsReadingRules.ruleResIds[4])
        assertEquals(R.string.rule_6, SettingsReadingRules.ruleResIds[5])

        val xmlContent = loadStringsXml()

        // Verify title
        val title = unescapeXmlString(extractStringResource(xmlContent, "reading_rules_title"))
        assertEquals("Reading rules", title)

        // Verify each rule text matches verbatim
        for (i in expectedRules.indices) {
            val key = expectedResourceKeys[i]
            val expectedText = expectedRules[i]
            val raw = extractStringResource(xmlContent, key)
            val actualText = unescapeXmlString(raw)

            assertEquals(
                "Rule ${i + 1} ($key) copy mismatch",
                expectedText,
                actualText
            )
        }
    }

    // ------------------------------------------------------------------------
    // 4. Name Editor Logic Verification
    // ------------------------------------------------------------------------

    @Test
    fun nameEditor_savesAndNormalizesName() {
        storageManager.setName("Sarah")
        assertEquals("Sarah", storageManager.settingsFlow.value.name)
        assertEquals("Sarah", storageManager.getSettings().name)

        // Whitespace-only normalized to null
        storageManager.setName("   ")
        assertNull(storageManager.settingsFlow.value.name)
        assertNull(storageManager.getSettings().name)

        // Null normalized to null
        storageManager.setName(null)
        assertNull(storageManager.settingsFlow.value.name)

        // Updating name
        storageManager.setName("  Jessica  ")
        assertEquals("Jessica", storageManager.settingsFlow.value.name)
    }

    // ------------------------------------------------------------------------
    // 5. Delete Data Confirmation Strings & Navigation
    // ------------------------------------------------------------------------

    @Test
    fun deleteDataDialog_copyMatchesExactStrings() {
        val xmlContent = loadStringsXml()

        val deleteButtonText = unescapeXmlString(extractStringResource(xmlContent, "delete_all_data"))
        assertEquals("Delete all my data", deleteButtonText)

        val confirmTitle = unescapeXmlString(extractStringResource(xmlContent, "delete_data_confirm_title"))
        assertEquals("Delete all data", confirmTitle)

        val confirmMsg = unescapeXmlString(extractStringResource(xmlContent, "delete_data_confirm_msg"))
        assertEquals("Are you sure you want to delete all your data? This cannot be undone.", confirmMsg)

        val cancelBtn = unescapeXmlString(extractStringResource(xmlContent, "cancel_button"))
        assertEquals("Cancel", cancelBtn)

        val deleteBtn = unescapeXmlString(extractStringResource(xmlContent, "delete_button"))
        assertEquals("Delete", deleteBtn)
    }

    // ------------------------------------------------------------------------
    // 6. Verifies Zero Forbidden Words Outside Verbatim Disclaimer
    // ------------------------------------------------------------------------

    @Test
    fun forbiddenWords_strictlyExcludedOutsideVerbatimDisclaimer() {
        val xmlContent = loadStringsXml()

        // Extract all <string name="...">...</string>
        val stringEntryRegex = Regex("""<string\s+name="([^"]+)">([^<]*)</string>""")
        val matches = stringEntryRegex.findAll(xmlContent)

        for (match in matches) {
            val key = match.groupValues[1]
            val value = unescapeXmlString(match.groupValues[2])

            // Skip about_disclaimer which contains "therapy" as allowed in Section 4 (F7)
            if (key == "about_disclaimer") continue

            val lowerValue = value.lowercase()
            for (forbidden in forbiddenWords) {
                assertFalse(
                    "Forbidden word '$forbidden' must never appear in string resource '$key': '$value'",
                    lowerValue.contains(forbidden)
                )
            }
        }

        // Verify SettingsScreen.kt contains zero forbidden words
        val settingsFile = findFile("app/src/main/java/com/jovoc/dailyaffirmationwomen/ui/settings/SettingsScreen.kt")
        val settingsCode = settingsFile.readText().lowercase()
        for (forbidden in forbiddenWords) {
            assertFalse(
                "Forbidden word '$forbidden' must never appear in SettingsScreen.kt",
                settingsCode.contains(forbidden)
            )
        }

        // Verify AboutScreen.kt contains zero forbidden words outside of about_disclaimer references
        val aboutFile = findFile("app/src/main/java/com/jovoc/dailyaffirmationwomen/ui/settings/AboutScreen.kt")
        val aboutCode = aboutFile.readText()
        // Strip comments and disclaimer references
        val strippedAboutCode = aboutCode
            .lines()
            .filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") || it.trim().startsWith("/*") || it.contains("R.string.about_disclaimer") }
            .joinToString("\n")
            .lowercase()

        for (forbidden in forbiddenWords) {
            assertFalse(
                "Forbidden word '$forbidden' must never appear in AboutScreen.kt UI code",
                strippedAboutCode.contains(forbidden)
            )
        }
    }

    // ------------------------------------------------------------------------
    // 7. Internal Level Names Never Exposed to the User
    // ------------------------------------------------------------------------

    @Test
    fun internalLevelNames_neverShownInSettingsOrAboutUI() {
        val settingsFile = findFile("app/src/main/java/com/jovoc/dailyaffirmationwomen/ui/settings/SettingsScreen.kt")
        val settingsCode = settingsFile.readText().lowercase()

        for (levelName in internalLevelNames) {
            assertFalse(
                "Internal level name '$levelName' must never appear in SettingsScreen.kt",
                settingsCode.contains("\"$levelName\"")
            )
        }

        val aboutFile = findFile("app/src/main/java/com/jovoc/dailyaffirmationwomen/ui/settings/AboutScreen.kt")
        val aboutCode = aboutFile.readText().lowercase()

        for (levelName in internalLevelNames) {
            assertFalse(
                "Internal level name '$levelName' must never appear in AboutScreen.kt",
                aboutCode.contains("\"$levelName\"")
            )
        }
    }

    // ------------------------------------------------------------------------
    // Helper Methods
    // ------------------------------------------------------------------------

    private fun loadStringsXml(): String {
        val candidates = listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml"),
            File("../app/src/main/res/values/strings.xml")
        )
        for (f in candidates) {
            if (f.exists()) return f.readText()
        }
        throw IllegalStateException("strings.xml not found in candidates: $candidates")
    }

    private fun findFile(path: String): File {
        val candidates = listOf(
            File(path),
            File("../$path"),
            File(path.removePrefix("app/"))
        )
        for (f in candidates) {
            if (f.exists()) return f
        }
        throw IllegalStateException("File '$path' not found in candidates: $candidates")
    }

    private fun extractStringResource(xmlContent: String, stringName: String): String {
        val regex = Regex("""<string\s+name="$stringName">([^<]*)</string>""")
        val match = regex.find(xmlContent)
            ?: throw IllegalArgumentException("String resource '$stringName' not found")
        return match.groupValues[1]
    }

    private fun unescapeXmlString(xmlString: String): String {
        return xmlString
            .replace("\\\"", "\"")
            .replace("\\'", "'")
    }

    // ------------------------------------------------------------------------
    // In-Memory Fake SharedPreferences for Deterministic Testing
    // ------------------------------------------------------------------------

    private class FakeSharedPreferences : SharedPreferences {
        val data = ConcurrentHashMap<String, Any>()

        override fun getAll(): Map<String, *> = HashMap(data)

        override fun getString(key: String?, defValue: String?): String? {
            return data[key] as? String ?: defValue
        }

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? {
            return (data[key] as? Set<String>) ?: defValues
        }

        override fun getInt(key: String?, defValue: Int): Int {
            return (data[key] as? Int) ?: defValue
        }

        override fun getLong(key: String?, defValue: Long): Long {
            return (data[key] as? Long) ?: defValue
        }

        override fun getFloat(key: String?, defValue: Float): Float {
            return (data[key] as? Float) ?: defValue
        }

        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            return (data[key] as? Boolean) ?: defValue
        }

        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor(this)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val prefs: FakeSharedPreferences) : SharedPreferences.Editor {
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
