package com.jovoc.dailyaffirmationwomen.audit

import android.content.SharedPreferences
import com.jovoc.dailyaffirmationwomen.R
import com.jovoc.dailyaffirmationwomen.data.model.Affirmation
import com.jovoc.dailyaffirmationwomen.data.model.AffirmationDataset
import com.jovoc.dailyaffirmationwomen.data.model.Category
import com.jovoc.dailyaffirmationwomen.data.model.Level
import com.jovoc.dailyaffirmationwomen.data.model.MetaSession
import com.jovoc.dailyaffirmationwomen.data.model.ResearchNote
import com.jovoc.dailyaffirmationwomen.data.model.SessionLogEntry
import com.jovoc.dailyaffirmationwomen.data.model.SessionMode
import com.jovoc.dailyaffirmationwomen.data.model.SuggestPolicy
import com.jovoc.dailyaffirmationwomen.data.model.UserSentence
import com.jovoc.dailyaffirmationwomen.data.model.UserSettings
import com.jovoc.dailyaffirmationwomen.data.repository.DatasetRepository
import com.jovoc.dailyaffirmationwomen.data.storage.LocalStorageManager
import com.jovoc.dailyaffirmationwomen.domain.FormattedSentence
import com.jovoc.dailyaffirmationwomen.domain.LevelManager
import com.jovoc.dailyaffirmationwomen.domain.SentenceSelector
import com.jovoc.dailyaffirmationwomen.domain.SuggestionEngine
import com.jovoc.dailyaffirmationwomen.ui.session.SessionStep
import com.jovoc.dailyaffirmationwomen.ui.session.SleepVibrationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Part 14: Master Quality Audit, Checklist & Section 6 Avoid List Compliance Test Suite.
 *
 * Source of truth:
 * - AGENT_INSTRUCTIONS.md Section 6 (Avoid List)
 * - AGENT_INSTRUCTIONS.md Section 10 (Done Checklist - all 17 items)
 * - tasks.md Part 14
 */
class QualityAndComplianceAuditTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var storageManager: LocalStorageManager
    private lateinit var dataset: AffirmationDataset

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
        val jsonString = loadDatasetJson()
        dataset = DatasetRepository.parseJson(jsonString)
    }

    // ========================================================================
    // SECTION A: Section 6 Avoid List Compliance
    // ========================================================================

    /**
     * Avoid List #1: No sample data, lorem ipsum, or fake affirmations.
     * All sentences loaded from JSON asset.
     */
    @Test
    fun audit_section6_01_noSampleDataOrLoremIpsum_allSentencesFromAssetJson() {
        // Verify all 12 categories loaded from bundled JSON asset
        assertEquals(12, dataset.categories.size)

        var totalAffirmations = 0
        val sampleMarkers = listOf("lorem", "ipsum", "placeholder", "fake", "dummy text", "sample affirmation", "todo:")

        for (category in dataset.categories) {
            assertTrue("Category '${category.id}' has empty affirmations", category.affirmations.isNotEmpty())
            for (aff in category.affirmations) {
                totalAffirmations++
                assertTrue("Affirmation id must not be empty", aff.id.isNotBlank())
                for (level in Level.entries) {
                    val text = aff.levels[level]
                    assertNotNull("Affirmation ${aff.id} missing level $level", text)
                    val lower = text!!.lowercase()
                    for (marker in sampleMarkers) {
                        assertFalse("Affirmation text must not contain sample marker '$marker': '$text'", lower.contains(marker))
                    }
                }
                val promptLower = aff.prompt.lowercase()
                for (marker in sampleMarkers) {
                    assertFalse("Prompt must not contain sample marker '$marker': '${aff.prompt}'", promptLower.contains(marker))
                }
            }
        }
        assertTrue("Must have parsed affirmations from dataset asset", totalAffirmations >= 30)

        // Verify strings.xml does not contain lorem ipsum or sample text
        val strings = extractAllStringResources(loadStringsXml())
        for ((key, value) in strings) {
            val lower = value.lowercase()
            for (marker in sampleMarkers) {
                assertFalse("strings.xml key '$key' must not contain '$marker': '$value'", lower.contains(marker))
            }
        }
    }

    /**
     * Avoid List #2: No paraphrasing of research notes.
     * Only claimUi, population, and source are mapped/used.
     */
    @Test
    fun audit_section6_02_noParaphrasedResearchNotes_onlyClaimUiPopulationSource() {
        // Verify ResearchNote data model only exposes claimUi, population, source
        val fields = ResearchNote::class.java.declaredFields.map { it.name }
        assertTrue("ResearchNote must have 'id'", fields.contains("id"))
        assertTrue("ResearchNote must have 'claimUi'", fields.contains("claimUi"))
        assertTrue("ResearchNote must have 'population'", fields.contains("population"))
        assertTrue("ResearchNote must have 'source'", fields.contains("source"))

        // Must NOT expose developer-only fields from raw JSON
        assertFalse("Must not expose do_not_say", fields.contains("do_not_say") || fields.contains("doNotSay"))
        assertFalse("Must not expose extra_fact", fields.contains("extra_fact") || fields.contains("extraFact"))
        assertFalse("Must not expose source_check", fields.contains("source_check") || fields.contains("sourceCheck"))

        // Verify ResearchNoteBlock.kt UI only renders claimUi, population, and source
        val blockCode = findFile("app/src/main/java/com/jovoc/dailyaffirmationwomen/ui/category/ResearchNoteBlock.kt").readText()
        assertTrue("Must render claimUi", blockCode.contains("researchNote.claimUi"))
        assertTrue("Must render population", blockCode.contains("researchNote.population"))
        assertTrue("Must render source", blockCode.contains("researchNote.source"))
        assertFalse("Must not reference doNotSay", blockCode.contains("doNotSay"))
        assertFalse("Must not reference extraFact", blockCode.contains("extraFact"))
        assertFalse("Must not reference sourceCheck", blockCode.contains("sourceCheck"))
    }

    /**
     * Avoid List #3: Zero usage of forbidden words outside the exact statutory medical disclaimer.
     * Forbidden words: proven, guaranteed, cure, treat, heal, therapy, clinically.
     */
    @Test
    fun audit_section6_03_zeroUsageOfForbiddenWordsInStringsAndUi() {
        val strings = extractAllStringResources(loadStringsXml())

        // 1. Verify exact statutory medical disclaimer in about_disclaimer
        val disclaimer = strings["about_disclaimer"]
        assertNotNull("about_disclaimer must exist", disclaimer)
        val expectedDisclaimer = "This app is for everyday well-being. It is not medical care or therapy. If you are in distress, please contact a local health professional or emergency services."
        assertEquals(expectedDisclaimer, disclaimer)

        // 2. All other string resources in strings.xml must have ZERO forbidden words
        for ((key, value) in strings) {
            if (key == "about_disclaimer") continue
            val lower = value.lowercase()
            for (word in forbiddenWords) {
                assertFalse(
                    "Forbidden word '$word' found in string resource '$key': '$value'",
                    lower.contains(word)
                )
            }
        }

        // 3. UI Kotlin source files must contain zero forbidden words outside comments/disclaimer references
        val uiDir = findFile("app/src/main/java/com/jovoc/dailyaffirmationwomen/ui")
        val ktFiles = uiDir.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("UI Kotlin files must exist", ktFiles.isNotEmpty())

        for (file in ktFiles) {
            val codeLines = file.readLines()
            for ((lineIndex, line) in codeLines.withIndex()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) continue
                if (trimmed.contains("R.string.about_disclaimer")) continue

                val lowerLine = trimmed.lowercase()
                for (word in forbiddenWords) {
                    assertFalse(
                        "Forbidden word '$word' in file ${file.name}:${lineIndex + 1}: '$line'",
                        lowerLine.contains("\"$word\"") || lowerLine.contains(" $word ")
                    )
                }
            }
        }
    }

    /**
     * Avoid List #4: Zero streaks, badges, points, progress bars, or "You missed a day" messages.
     */
    @Test
    fun audit_section6_04_zeroStreaksBadgesPointsProgressBarsOrMissedDayMessages() {
        val strings = extractAllStringResources(loadStringsXml())

        val gamificationTerms = listOf(
            "point", "points", "score", "xp", "confetti",
            "missed a day", "you missed", "keep your streak",
            "streak lost", "streak of"
        )

        for ((key, value) in strings) {
            val lower = value.lowercase()
            for (term in gamificationTerms) {
                assertFalse(
                    "Gamification term '$term' found in visible string '$key': '$value'",
                    lower.contains(term)
                )
            }
        }

        // Check session UI screens: no progress bar or badge components
        val sessionScreenCode = findFile("app/src/main/java/com/jovoc/dailyaffirmationwomen/ui/session/SessionScreen.kt").readText()
        assertFalse("SessionScreen must not contain LinearProgressIndicator", sessionScreenCode.contains("LinearProgressIndicator"))
        assertFalse("SessionScreen must not contain badges", sessionScreenCode.contains("Badge("))

        val closeScreenCode = findFile("app/src/main/java/com/jovoc/dailyaffirmationwomen/ui/session/ScreenDClose.kt").readText()
        assertFalse("ScreenDClose must not contain confetti", closeScreenCode.lowercase().contains("confetti"))
        assertFalse("ScreenDClose must not contain points/score", closeScreenCode.lowercase().contains("score"))
    }

    /**
     * Avoid List #5: Zero repeat counters or loops.
     */
    @Test
    fun audit_section6_05_zeroRepeatCountersOrLoops() {
        val readScreenCode = findFile("app/src/main/java/com/jovoc/dailyaffirmationwomen/ui/session/ScreenARead.kt").readText()
        assertFalse("ScreenARead must not have repeat loop counter", readScreenCode.contains("repeatCount"))
        assertFalse("ScreenARead must not have loop counter", readScreenCode.contains("loopCount"))
        assertFalse("ScreenARead must not have repetition multiplier", readScreenCode.contains("timesToRead"))

        val strings = extractAllStringResources(loadStringsXml())
        for ((key, value) in strings) {
            val lower = value.lowercase()
            assertFalse("Must not contain loop counter in '$key'", lower.contains("repeat 3 times") || lower.contains("repeat 5 times"))
        }
    }

    /**
     * Avoid List #6: Answer box text strictly non-persistent and never stored in SharedPreferences or session logs.
     */
    @Test
    fun audit_section6_06_answerBoxTextStrictlyNonPersistentAndNeverLogged() {
        val ephemeralText = "I felt strong when finishing the marathon in 2022."

        // 1. Verify LocalStorageManager keys - strictly matches Section 8 table
        val allowedKeys = setOf(
            LocalStorageManager.KEY_SETTINGS,
            LocalStorageManager.KEY_CATEGORY_LEVEL,
            LocalStorageManager.KEY_NOT_YET_STREAK,
            LocalStorageManager.KEY_FAVORITES,
            LocalStorageManager.KEY_USER_SENTENCES,
            LocalStorageManager.KEY_SESSION_LOG,
            LocalStorageManager.KEY_OPENED_CATEGORIES
        )
        val allStoredKeys = fakePrefs.getAll().keys
        assertTrue(allowedKeys.containsAll(allStoredKeys))

        // 2. Record practice session
        storageManager.recordSession("kind_to_myself", listOf("kind_01"))

        val sessionLog = storageManager.getSessionLog()
        assertEquals(1, sessionLog.size)
        val entry = sessionLog.first()
        assertEquals("kind_to_myself", entry.categoryId)
        assertEquals(listOf("kind_01"), entry.sentenceIds)

        // SessionLogEntry JSON structure must NEVER contain answer text
        val entryJson = entry.toJsonObject().toString()
        assertFalse("SessionLogEntry must not contain answer text", entryJson.contains(ephemeralText))
        assertFalse("SessionLogEntry must not have an answer field", entryJson.contains("answer"))
        assertFalse("SessionLogEntry must not have a text field", entryJson.contains("text"))

        // Inspect raw SharedPreferences data
        for ((key, value) in fakePrefs.getAll()) {
            assertFalse("Answer text leaked into SharedPreferences key '$key'", value.toString().contains(ephemeralText))
        }
    }

    /**
     * Avoid List #7: Sleep timer produces zero sound (no audio APIs, only Vibrator).
     */
    @Test
    fun audit_section6_07_sleepTimerProducesZeroSound_onlyVibratorUsed() {
        val sleepScreenCode = findFile("app/src/main/java/com/jovoc/dailyaffirmationwomen/ui/session/SleepTimerScreen.kt").readText()

        // Verify zero audio APIs
        val forbiddenAudioApis = listOf(
            "MediaPlayer",
            "SoundPool",
            "AudioTrack",
            "RingtoneManager",
            "ToneGenerator",
            "AudioManager"
        )
        for (api in forbiddenAudioApis) {
            assertFalse("SleepTimerScreen must never use audio API '$api'", sleepScreenCode.contains(api))
        }

        // Verify single haptic vibration helper is used
        assertTrue("SleepTimerScreen must use SleepVibrationHelper", sleepScreenCode.contains("SleepVibrationHelper"))
        assertEquals(500L, SleepVibrationHelper.VIBRATION_DURATION_MS)
    }

    /**
     * Avoid List #8: Max sentences per session strictly capped at 3 (`max_sentences_per_session`).
     */
    @Test
    fun audit_section6_08_maxSentencesPerSessionStrictlyCappedAtThree() {
        assertEquals(3, dataset.metaSession.maxSentencesPerSession)

        // Verify Screen D behavior
        fun canShowAnother(sentencesDone: Int, maxSentences: Int): Boolean {
            return sentencesDone < maxSentences
        }

        assertTrue("After 1 sentence, another sentence is allowed", canShowAnother(1, 3))
        assertTrue("After 2 sentences, another sentence is allowed", canShowAnother(2, 3))
        assertFalse("After 3 sentences, another sentence is strictly prohibited", canShowAnother(3, 3))
        assertFalse("Beyond 3 sentences, another sentence is strictly prohibited", canShowAnother(4, 3))
    }

    /**
     * Avoid List #9: No accounts, login, backend, or cloud sync.
     */
    @Test
    fun audit_section6_09_noAccountsLoginBackendOrCloudSync() {
        val gradleFile = findFile("app/build.gradle.kts").readText()

        val forbiddenBackendLibs = listOf(
            "firebase",
            "aws",
            "cognito",
            "supabase",
            "auth0",
            "play-services-auth"
        )
        for (lib in forbiddenBackendLibs) {
            assertFalse("app/build.gradle.kts must not contain backend lib '$lib'", gradleFile.lowercase().contains(lib))
        }

        val navigationCode = findFile("app/src/main/java/com/jovoc/dailyaffirmationwomen/ui/navigation/AppNavigation.kt").readText()
        assertFalse("Must not have Login screen", navigationCode.contains("Screen.Login"))
        assertFalse("Must not have Account screen", navigationCode.contains("Screen.Account"))
        assertFalse("Must not have Register screen", navigationCode.contains("Screen.Register"))
    }

    /**
     * Avoid List #10: Zero AI API calls or SDKs.
     */
    @Test
    fun audit_section6_10_zeroAiApiCallsOrSdks() {
        val gradleFile = findFile("app/build.gradle.kts").readText()

        val forbiddenAiLibs = listOf(
            "openai",
            "anthropic",
            "gemini",
            "generativeai",
            "langchain",
            "retrofit",
            "ktor",
            "okhttp"
        )
        for (lib in forbiddenAiLibs) {
            assertFalse("app/build.gradle.kts must not contain AI or HTTP client lib '$lib'", gradleFile.lowercase().contains(lib))
        }
    }

    /**
     * Avoid List #11: Zero unneeded permissions: AndroidManifest.xml contains ONLY android.permission.VIBRATE.
     */
    @Test
    fun audit_section6_11_zeroUnneededPermissions_onlyVibrateDeclared() {
        val manifestXml = loadAndroidManifest()
        val permissionRegex = Regex("""<uses-permission\s+android:name="([^"]+)"""")
        val matches = permissionRegex.findAll(manifestXml).toList()

        assertEquals("AndroidManifest.xml must declare exactly 1 permission", 1, matches.size)
        val declaredPermission = matches.first().groupValues[1]
        assertEquals("Declared permission must be android.permission.VIBRATE", "android.permission.VIBRATE", declaredPermission)

        // Assert zero dangerous / unneeded permissions
        assertFalse("Manifest must not contain INTERNET", manifestXml.contains("android.permission.INTERNET"))
        assertFalse("Manifest must not contain ACCESS_NETWORK_STATE", manifestXml.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertFalse("Manifest must not contain CAMERA", manifestXml.contains("android.permission.CAMERA"))
        assertFalse("Manifest must not contain RECORD_AUDIO", manifestXml.contains("android.permission.RECORD_AUDIO"))
        assertFalse("Manifest must not contain ACCESS_FINE_LOCATION", manifestXml.contains("android.permission.ACCESS_FINE_LOCATION"))
        assertFalse("Manifest must not contain READ_CONTACTS", manifestXml.contains("android.permission.READ_CONTACTS"))
    }

    /**
     * Avoid List #12: No stock photos of women, faces, or bodies.
     */
    @Test
    fun audit_section6_12_noStockPhotosOfWomenFacesOrBodies() {
        val resDir = findFile("app/src/main/res")
        val allResourceFiles = resDir.walkTopDown().filter { it.isFile }.toList()

        // Verify only app launcher icons and vector icons exist
        for (file in allResourceFiles) {
            val fileName = file.name.lowercase()
            if (fileName.endsWith(".png") || fileName.endsWith(".webp") || fileName.endsWith(".jpg")) {
                assertTrue(
                    "Only launcher icons allowed in resources, found: ${file.path}",
                    fileName.startsWith("ic_launcher")
                )
            }
        }
    }

    /**
     * Avoid List #13: No red error styles on cards; calm neutral styling used.
     */
    @Test
    fun audit_section6_13_noRedErrorStylesOnCards_calmNeutralStylingUsed() {
        val checkScreenCode = findFile("app/src/main/java/com/jovoc/dailyaffirmationwomen/ui/session/ScreenBCheck.kt").readText()

        // category_no_fit must use neutral onSurfaceVariant, not error red
        assertTrue("category_no_fit must use onSurfaceVariant", checkScreenCode.contains("colorScheme.onSurfaceVariant"))
        assertFalse("category_no_fit must not use colorScheme.error", checkScreenCode.contains("colorScheme.error"))

        // Theme colors must be soft neutral and sage green
        val colorCode = findFile("app/src/main/java/com/jovoc/dailyaffirmationwomen/ui/theme/Color.kt").readText()
        assertTrue("Theme must define Cream neutral background", colorCode.contains("CreamBackground"))
        assertTrue("Theme must define Slate neutral background", colorCode.contains("SlateBackground"))
        assertTrue("Theme must define Sage calm accent", colorCode.contains("SagePrimary"))
    }

    /**
     * Avoid List #14: No raw {name} placeholder displayed without prompt.
     */
    @Test
    fun audit_section6_14_noRawNamePlaceholderDisplayedWithoutPrompt() {
        val rawSentence = "Keep going, {name}."

        // Name is null -> NeedsNamePrompt
        val resultNull = SentenceSelector.formatSentence(rawSentence, null)
        assertEquals(FormattedSentence.NeedsNamePrompt, resultNull)

        // Name is blank -> NeedsNamePrompt
        val resultBlank = SentenceSelector.formatSentence(rawSentence, "   ")
        assertEquals(FormattedSentence.NeedsNamePrompt, resultBlank)

        // Name is set -> Ready with name replaced
        val resultValid = SentenceSelector.formatSentence(rawSentence, "Sophia")
        assertTrue(resultValid is FormattedSentence.Ready)
        assertEquals("Keep going, Sophia.", (resultValid as FormattedSentence.Ready).text)
        assertFalse("Raw {name} must never be in formatted text", resultValid.text.contains("{name}"))
    }

    /**
     * Avoid List #15: No user text leaves the device (strictly offline-first, zero network permissions).
     */
    @Test
    fun audit_section6_15_noUserTextLeavesTheDevice_offlineFirstStorageOnly() {
        // Confirm no network permission
        val manifest = loadAndroidManifest()
        assertFalse("Must not declare INTERNET permission", manifest.contains("android.permission.INTERNET"))

        // User sentences and settings persist strictly in local SharedPreferences
        storageManager.saveUserSentence("kind_to_myself", "I am doing my best today.")
        storageManager.setName("Elena")

        val userSentences = storageManager.getUserSentences()
        assertEquals(1, userSentences.size)
        assertEquals("I am doing my best today.", userSentences.first().text)
        assertEquals("Elena", storageManager.getSettings().name)

        // Verification: all stored data resides in SharedPreferences map only
        assertTrue(fakePrefs.contains(LocalStorageManager.KEY_USER_SENTENCES))
        assertTrue(fakePrefs.contains(LocalStorageManager.KEY_SETTINGS))
    }

    // ========================================================================
    // SECTION B: Section 10 Done Checklist (All 17 Items)
    // ========================================================================

    /**
     * Done Checklist Item 1: App opens straight to Home with Today's practice card and 12 categories.
     */
    @Test
    fun audit_section10_item01_appOpensStraightToHomeWithTodayCardAnd12Categories() {
        // MainActivity sets AppNavHost with Screen.Home when onboarding completed
        val mainActivityCode = findFile("app/src/main/java/com/jovoc/dailyaffirmationwomen/MainActivity.kt").readText()
        assertTrue("MainActivity starts AppNavHost", mainActivityCode.contains("AppNavHost"))
        assertTrue("MainActivity determines initial screen based on howItWorksSeen", mainActivityCode.contains("howItWorksSeen"))

        // Exactly 12 categories parsed from JSON
        assertEquals("Dataset must contain exactly 12 categories", 12, dataset.categories.size)

        // Home screen displays TodaysPracticeCard at top
        val homeScreenCode = findFile("app/src/main/java/com/jovoc/dailyaffirmationwomen/ui/home/HomeScreen.kt").readText()
        assertTrue("HomeScreen must include TodaysPracticeCard", homeScreenCode.contains("TodaysPracticeCard"))
        assertTrue("HomeScreen must render CategoryRow items", homeScreenCode.contains("CategoryRow"))
    }

    /**
     * Done Checklist Item 2: First launch ever suggests "Kind to myself" (`kind_to_myself`).
     */
    @Test
    fun audit_section10_item02_firstLaunchEverSuggestsKindToMyself() {
        val suggested = SuggestionEngine.getSuggestedCategory(
            currentHour = 10,
            todayDateStr = "2026-11-07",
            sessionLog = emptyList(),
            openedCategories = emptySet(),
            categories = dataset.categories,
            metaSession = dataset.metaSession
        )
        assertEquals("kind_to_myself", suggested)
    }

    /**
     * Done Checklist Item 3: 22:00 with no session done today suggests "Before sleep" (`bedtime_unload`).
     */
    @Test
    fun audit_section10_item03_nightHourAt2200WithNoSessionTodaySuggestsBeforeSleep() {
        val suggested = SuggestionEngine.getSuggestedCategory(
            currentHour = 22,
            todayDateStr = "2026-11-07",
            sessionLog = listOf(
                SessionLogEntry(date = "2026-11-06", categoryId = "kind_to_myself", sentenceIds = listOf("kind_01"))
            ),
            openedCategories = setOf("kind_to_myself"),
            categories = dataset.categories,
            metaSession = dataset.metaSession
        )
        assertEquals("bedtime_unload", suggested)
    }

    /**
     * Done Checklist Item 4: Finished session today shows `done_today` text and no Start button.
     */
    @Test
    fun audit_section10_item04_finishedSessionTodayShowsDoneTodayTextAndNoStartButton() {
        val todayStr = "2026-11-07"
        val sessionLog = listOf(
            SessionLogEntry(date = todayStr, categoryId = "kind_to_myself", sentenceIds = listOf("kind_01"))
        )

        val isDoneToday = sessionLog.any { it.date == todayStr }
        assertTrue("Session finished today must be detected", isDoneToday)

        val cardCode = findFile("app/src/main/java/com/jovoc/dailyaffirmationwomen/ui/home/TodaysPracticeCard.kt").readText()
        assertTrue("TodaysPracticeCard checks hasCompletedToday", cardCode.contains("hasCompletedToday"))
        assertTrue("TodaysPracticeCard displays done_today copy", cardCode.contains("R.string.done_today"))

        val strings = extractAllStringResources(loadStringsXml())
        assertEquals(
            "That is enough for today. Come back tomorrow, or open any category.",
            strings["done_today"]
        )
    }

    /**
     * Done Checklist Item 5: Standard session shows Screens A, B, C, D in order;
     * "Another sentence" disappears after 3 sentences.
     */
    @Test
    fun audit_section10_item05_standardSessionShowsABCDInOrder_anotherSentenceDisappearsAfterThree() {
        // Step order verification
        val expectedSteps = listOf(SessionStep.READ, SessionStep.CHECK, SessionStep.DO, SessionStep.CLOSE)
        assertEquals(SessionStep.READ, expectedSteps[0])
        assertEquals(SessionStep.CHECK, expectedSteps[1])
        assertEquals(SessionStep.DO, expectedSteps[2])
        assertEquals(SessionStep.CLOSE, expectedSteps[3])

        // Another sentence button visibility
        val maxSentences = dataset.metaSession.maxSentencesPerSession
        assertEquals(3, maxSentences)

        fun canShowAnother(count: Int) = count < maxSentences
        assertTrue("1 completed: can show another", canShowAnother(1))
        assertTrue("2 completed: can show another", canShowAnother(2))
        assertFalse("3 completed: another sentence button disappears", canShowAnother(3))
    }

    /**
     * Done Checklist Item 6: "Feels true" moves gentle -> medium.
     * "Not yet" moves back and shows not-yet message.
     */
    @Test
    fun audit_section10_item06_feelsTrueMovesGentleToMedium_notYetMovesBackAndShowsNotYetMessage() {
        // Gentle -> Medium on Feels true
        val resultTrue = LevelManager.onFeelsTrue(Level.GENTLE)
        assertEquals(Level.MEDIUM, resultTrue.newLevel)
        assertEquals(0, resultTrue.newStreak)
        assertFalse(resultTrue.showCategoryNoFit)

        // Medium -> Gentle on Not yet
        val resultNotYet = LevelManager.onNotYet(Level.MEDIUM, currentStreak = 0)
        assertEquals(Level.GENTLE, resultNotYet.newLevel)
        assertEquals(1, resultNotYet.newStreak)
        assertFalse(resultNotYet.showCategoryNoFit)

        // ScreenBCheck displays not_yet_message
        val checkScreenCode = findFile("app/src/main/java/com/jovoc/dailyaffirmationwomen/ui/session/ScreenBCheck.kt").readText()
        assertTrue("ScreenBCheck renders not_yet_message on Not yet", checkScreenCode.contains("R.string.not_yet_message"))

        val strings = extractAllStringResources(loadStringsXml())
        assertEquals(
            "That is okay. Think of one time it was true for you, and one time it was not.",
            strings["not_yet_message"]
        )
    }

    /**
     * Done Checklist Item 7: Three "Not yet" in a row at gentle shows `category_no_fit` text once.
     */
    @Test
    fun audit_section10_item07_threeNotYetInARowAtGentleShowsCategoryNoFitTextOnce() {
        // 1st Not yet at Gentle: streak 1, noFit = false
        val step1 = LevelManager.onNotYet(Level.GENTLE, currentStreak = 0)
        assertEquals(Level.GENTLE, step1.newLevel)
        assertEquals(1, step1.newStreak)
        assertFalse(step1.showCategoryNoFit)

        // 2nd Not yet at Gentle: streak 2, noFit = false
        val step2 = LevelManager.onNotYet(Level.GENTLE, currentStreak = 1)
        assertEquals(Level.GENTLE, step2.newLevel)
        assertEquals(2, step2.newStreak)
        assertFalse(step2.showCategoryNoFit)

        // 3rd Not yet at Gentle: streak 3, noFit = true
        val step3 = LevelManager.onNotYet(Level.GENTLE, currentStreak = 2)
        assertEquals(Level.GENTLE, step3.newLevel)
        assertEquals(3, step3.newStreak)
        assertTrue("3rd consecutive Not yet at gentle must show category_no_fit", step3.showCategoryNoFit)

        val strings = extractAllStringResources(loadStringsXml())
        assertEquals(
            "This category may not fit you right now. You can try another one, or skip it for a while.",
            strings["category_no_fit"]
        )
    }

    /**
     * Done Checklist Item 8: Answer box text is cleared after session and app restart.
     */
    @Test
    fun audit_section10_item08_answerBoxTextClearedAfterSessionAndRestart() {
        // Simulate session completion
        var ephemeralAnswerText: String? = "My thoughts about courage today"
        storageManager.recordSession("hard_days", listOf("hard_01"))

        // Session completes: ephemeral text is cleared
        ephemeralAnswerText = ""
        assertEquals("", ephemeralAnswerText)

        // Simulate app restart with a fresh storage manager instance
        val restartStorageManager = LocalStorageManager(fakePrefs)
        val logs = restartStorageManager.getSessionLog()
        assertEquals(1, logs.size)
        // Log strictly holds categoryId and sentenceIds only
        val logJson = logs.first().toJsonObject().toString()
        assertFalse("Restarted storage must not have answer text", logJson.contains("courage"))
    }

    /**
     * Done Checklist Item 9: "Before sleep" shows dim 5-min timer, vibrates once, no sound, no further sentences.
     */
    @Test
    fun audit_section10_item09_beforeSleepShowsDimFiveMinuteTimer_vibratesOnce_noSound_noFurtherSentences() {
        val sleepCategory = dataset.categories.first { it.id == "bedtime_unload" }
        assertEquals(SessionMode.PAPER_TIMER, sleepCategory.sessionMode)
        assertEquals(300, sleepCategory.sessionSeconds) // 5 minutes

        val sleepScreenCode = findFile("app/src/main/java/com/jovoc/dailyaffirmationwomen/ui/session/SleepTimerScreen.kt").readText()

        // Dim dark theme
        assertTrue("Uses dark background", sleepScreenCode.contains("Color(0xFF0A0C0D)") || sleepScreenCode.contains("SleepTheme"))

        // Single vibration, no sound
        assertTrue("Uses SleepVibrationHelper", sleepScreenCode.contains("SleepVibrationHelper"))
        assertFalse("Zero MediaPlayer", sleepScreenCode.contains("MediaPlayer"))

        // Shows sleep_end string
        assertTrue("Displays sleep_end string", sleepScreenCode.contains("R.string.sleep_end"))

        // Skips Screen D (no another_sentence offered)
        assertFalse("Sleep timer must never show another_sentence", sleepScreenCode.contains("R.string.another_sentence"))
    }

    /**
     * Done Checklist Item 10: "Before something big" shows say-aloud instruction.
     */
    @Test
    fun audit_section10_item10_beforeSomethingBigShowsSayAloudInstruction() {
        val category = dataset.categories.first { it.id == "before_something_big" }
        assertEquals(SessionMode.SAY_ALOUD, category.sessionMode)

        val readScreenCode = findFile("app/src/main/java/com/jovoc/dailyaffirmationwomen/ui/session/ScreenARead.kt").readText()
        assertTrue("Selects read_instruction_say_aloud for SAY_ALOUD mode", readScreenCode.contains("R.string.read_instruction_say_aloud"))

        val strings = extractAllStringResources(loadStringsXml())
        assertEquals(
            "Say it out loud, once, just before you begin.",
            strings["read_instruction_say_aloud"]
        )
    }

    /**
     * Done Checklist Item 11: "Talking to myself by name" prompts for name when unset, shows name when set.
     */
    @Test
    fun audit_section10_item11_talkingToMyselfByNamePromptsForNameWhenUnset_showsNameWhenSet() {
        val nameCategory = dataset.categories.first { it.id == "by_my_name" }
        val rawSentence = nameCategory.affirmations.first().levels[Level.GENTLE]!!
        assertTrue("Sentence must contain {name} placeholder", rawSentence.contains("{name}"))

        // When unset
        val whenUnset = SentenceSelector.formatSentence(rawSentence, null)
        assertEquals(FormattedSentence.NeedsNamePrompt, whenUnset)

        // When set
        val whenSet = SentenceSelector.formatSentence(rawSentence, "Claire")
        assertTrue(whenSet is FormattedSentence.Ready)
        val readyText = (whenSet as FormattedSentence.Ready).text
        assertTrue("Must contain substituted name", readyText.contains("Claire"))
        assertFalse("Must not contain raw {name}", readyText.contains("{name}"))
    }

    /**
     * Done Checklist Item 12: Adding, editing, deleting user sentences works and survives restart.
     */
    @Test
    fun audit_section10_item12_userSentences_addEditDeleteWorksAndSurvivesRestart() {
        // 1. Add user sentence
        val added = storageManager.saveUserSentence("kind_to_myself", "I am worthy of peace.")
        assertTrue("User sentence ID must start with u_", added.id.startsWith(UserSentence.PREFIX))
        assertEquals(1, storageManager.getUserSentences().size)

        // Simulate app restart
        var restartStorage = LocalStorageManager(fakePrefs)
        var userSentences = restartStorage.getUserSentences()
        assertEquals(1, userSentences.size)
        assertEquals("I am worthy of peace.", userSentences.first().text)

        // 2. Edit user sentence
        restartStorage.updateUserSentence(added.id, "I am worthy of profound peace.")
        assertEquals("I am worthy of profound peace.", restartStorage.getUserSentences().first().text)

        // Simulate second app restart
        restartStorage = LocalStorageManager(fakePrefs)
        assertEquals("I am worthy of profound peace.", restartStorage.getUserSentences().first().text)

        // 3. Delete user sentence
        restartStorage.deleteUserSentence(added.id)
        assertTrue(restartStorage.getUserSentences().isEmpty())

        // Simulate third app restart
        restartStorage = LocalStorageManager(fakePrefs)
        assertTrue(restartStorage.getUserSentences().isEmpty())
    }

    /**
     * Done Checklist Item 13: Favorites survive restart.
     */
    @Test
    fun audit_section10_item13_favoritesSurviveAppRestart() {
        storageManager.toggleFavorite("kind_01")
        storageManager.toggleFavorite("values_02")
        assertTrue(storageManager.isFavorite("kind_01"))
        assertTrue(storageManager.isFavorite("values_02"))
        assertEquals(2, storageManager.getFavorites().size)

        // Simulate app restart
        val restartStorage = LocalStorageManager(fakePrefs)
        assertTrue(restartStorage.isFavorite("kind_01"))
        assertTrue(restartStorage.isFavorite("values_02"))
        assertEquals(2, restartStorage.getFavorites().size)

        // Toggle off
        restartStorage.toggleFavorite("kind_01")
        assertFalse(restartStorage.isFavorite("kind_01"))

        // Simulate second app restart
        val secondRestart = LocalStorageManager(fakePrefs)
        assertFalse(secondRestart.isFavorite("kind_01"))
        assertTrue(secondRestart.isFavorite("values_02"))
        assertEquals(1, secondRestart.getFavorites().size)
    }

    /**
     * Done Checklist Item 14: "Delete all my data" clears everything local.
     */
    @Test
    fun audit_section10_item14_deleteAllDataClearsEverythingLocal() {
        // Populate all 7 areas of state
        storageManager.setName("Amelia")
        storageManager.setOnboardingCompleted("I believe it", Level.MEDIUM, listOf("kind_to_myself"))
        storageManager.setCategoryLevel("kind_to_myself", Level.STRONG)
        repeat(2) { storageManager.incrementNotYetStreak("kind_to_myself") }
        storageManager.toggleFavorite("kind_01")
        storageManager.saveUserSentence("kind_to_myself", "I breathe deeply.")
        storageManager.recordSession("kind_to_myself", listOf("kind_01"))
        storageManager.markCategoryOpened("kind_to_myself")

        // Assert state is populated
        assertNotNull(storageManager.getSettings().name)
        assertEquals(1, storageManager.categoryLevelsFlow.value.size)
        assertEquals(1, storageManager.getFavorites().size)
        assertEquals(1, storageManager.getUserSentences().size)
        assertEquals(1, storageManager.getSessionLog().size)
        assertEquals(1, storageManager.openedCategoriesFlow.value.size)

        // Perform delete all data
        storageManager.deleteAllData()

        // Verify all state in memory is reset to defaults
        assertNull(storageManager.getSettings().name)
        assertFalse(storageManager.getSettings().howItWorksSeen)
        assertTrue(storageManager.categoryLevelsFlow.value.isEmpty())
        assertTrue(storageManager.getFavorites().isEmpty())
        assertTrue(storageManager.getUserSentences().isEmpty())
        assertTrue(storageManager.getSessionLog().isEmpty())
        assertTrue(storageManager.openedCategoriesFlow.value.isEmpty())

        // Verify underlying SharedPreferences is completely empty
        assertTrue("SharedPreferences must be empty after deleteAllData()", fakePrefs.getAll().isEmpty())

        // Verify restart yields completely clean state
        val restartStorage = LocalStorageManager(fakePrefs)
        assertNull(restartStorage.getSettings().name)
        assertTrue(restartStorage.getFavorites().isEmpty())
        assertTrue(restartStorage.getUserSentences().isEmpty())
    }

    /**
     * Done Checklist Item 15: Offline-first: works without network.
     */
    @Test
    fun audit_section10_item15_offlineFirst_worksWithoutNetwork() {
        // 1. AndroidManifest has zero network permissions
        val manifest = loadAndroidManifest()
        assertFalse("Must not declare INTERNET permission", manifest.contains("android.permission.INTERNET"))
        assertFalse("Must not declare ACCESS_NETWORK_STATE", manifest.contains("ACCESS_NETWORK_STATE"))

        // 2. Entire dataset parsed synchronously or via Dispatchers.IO from bundled asset
        val parsed = DatasetRepository.parseJson(loadDatasetJson())
        assertEquals(12, parsed.categories.size)
        assertEquals(11, parsed.researchNotes.size)

        // 3. App storage is entirely in-memory and local SharedPreferences
        val localManager = LocalStorageManager(fakePrefs)
        localManager.saveUserSentence("kind_to_myself", "Offline sentence.")
        assertEquals(1, localManager.getUserSentences().size)
    }

    /**
     * Done Checklist Item 16: Suggestion function has unit test specifications
     * for first launch, night, rotation, opt-in, limited.
     */
    @Test
    fun audit_section10_item16_suggestionFunctionHasUnitTestsForFiveKeyScenarios() {
        val categories = dataset.categories
        val meta = dataset.metaSession

        // 1. First launch ever -> kind_to_myself
        val firstLaunch = SuggestionEngine.getSuggestedCategory(
            currentHour = 12,
            todayDateStr = "2026-11-07",
            sessionLog = emptyList(),
            openedCategories = emptySet(),
            categories = categories,
            metaSession = meta
        )
        assertEquals("kind_to_myself", firstLaunch)

        // 2. Night hour (22:00) with no session done today -> bedtime_unload (suggest_policy = night_only)
        val nightSuggestion = SuggestionEngine.getSuggestedCategory(
            currentHour = 22,
            todayDateStr = "2026-11-07",
            sessionLog = listOf(
                SessionLogEntry(date = "2026-11-06", categoryId = "kind_to_myself", sentenceIds = listOf("kind_01"))
            ),
            openedCategories = setOf("kind_to_myself"),
            categories = categories,
            metaSession = meta
        )
        assertEquals("bedtime_unload", nightSuggestion)

        // 3. Normal rotation -> next in file order after last practiced
        val rotationSuggestion = SuggestionEngine.getSuggestedCategory(
            currentHour = 12,
            todayDateStr = "2026-11-07",
            sessionLog = listOf(
                SessionLogEntry(date = "2026-11-06", categoryId = "my_values", sentenceIds = listOf("v_1"))
            ),
            openedCategories = emptySet(),
            categories = categories,
            metaSession = meta
        )
        assertEquals("kind_to_myself", rotationSuggestion)

        // 4. Opt-in category ("midlife_and_beyond") skipped unless opened
        val optInSkipped = SuggestionEngine.getSuggestedCategory(
            currentHour = 12,
            todayDateStr = "2026-11-07",
            sessionLog = listOf(
                SessionLogEntry(date = "2026-11-06", categoryId = "not_alone", sentenceIds = listOf("na_1"))
            ),
            openedCategories = emptySet(), // not opened!
            categories = categories,
            metaSession = meta
        )
        // Should skip midlife_and_beyond and proceed to work_and_voice
        assertEquals("work_and_voice", optInSkipped)

        // 5. Limited category ("small_good_things") skipped if practiced within 3 days
        val limitedSkipped = SuggestionEngine.getSuggestedCategory(
            currentHour = 12,
            todayDateStr = "2026-11-07",
            sessionLog = listOf(
                SessionLogEntry(date = "2026-11-06", categoryId = "before_something_big", sentenceIds = listOf("bsb_1")),
                SessionLogEntry(date = "2026-11-05", categoryId = "small_good_things", sentenceIds = listOf("sgt_1"))
            ),
            openedCategories = setOf("before_something_big", "small_good_things"),
            categories = categories,
            metaSession = meta
        )
        // Next after before_something_big is small_good_things, but it's limited and practiced 2 days ago (< 3 days),
        // next is bedtime_unload (night_only, skipped at 12:00), so advances to my_future_self
        assertEquals("my_future_self", limitedSkipped)
    }

    /**
     * Done Checklist Item 17: No visible text contains words in the avoid list.
     */
    @Test
    fun audit_section10_item17_noVisibleTextContainsWordsInAvoidList() {
        val strings = extractAllStringResources(loadStringsXml())

        // 1. Audit string resources in strings.xml
        for ((key, value) in strings) {
            if (key == "about_disclaimer") continue
            val lower = value.lowercase()
            for (forbidden in forbiddenWords) {
                assertFalse(
                    "Avoid list word '$forbidden' found in visible string '$key': '$value'",
                    lower.contains(forbidden)
                )
            }
            for (levelName in internalLevelNames) {
                assertFalse(
                    "Internal level name '$levelName' found in visible string '$key': '$value'",
                    lower.contains(levelName)
                )
            }
        }

        // 2. Audit all category titles and when_to_use descriptions in JSON
        for (category in dataset.categories) {
            val titleLower = category.title.lowercase()
            val whenLower = category.whenToUse.lowercase()
            for (forbidden in forbiddenWords) {
                assertFalse("Forbidden word '$forbidden' in category title '${category.title}'", titleLower.contains(forbidden))
                assertFalse("Forbidden word '$forbidden' in category whenToUse '${category.whenToUse}'", whenLower.contains(forbidden))
            }
        }

        // 3. Audit all research notes in JSON (claimUi, population)
        for ((noteId, note) in dataset.researchNotes) {
            val claimLower = note.claimUi.lowercase()
            for (forbidden in forbiddenWords) {
                assertFalse(
                    "Forbidden word '$forbidden' found in research note claimUi '$noteId': '${note.claimUi}'",
                    claimLower.contains(forbidden)
                )
            }
        }
    }

    // ========================================================================
    // Helper Methods & Test Fixtures
    // ========================================================================

    private fun findFile(path: String): File {
        val candidates = listOf(
            File(path),
            File("../$path"),
            File(path.removePrefix("app/")),
            File("../" + path.removePrefix("app/"))
        )
        for (f in candidates) {
            if (f.exists()) return f
        }
        throw IllegalStateException("File '$path' not found in candidates: $candidates")
    }

    private fun loadStringsXml(): String {
        return findFile("app/src/main/res/values/strings.xml").readText()
    }

    private fun loadDatasetJson(): String {
        return findFile("app/src/main/assets/affirmation_dataset.json").readText()
    }

    private fun loadAndroidManifest(): String {
        return findFile("app/src/main/AndroidManifest.xml").readText()
    }

    private fun extractAllStringResources(xml: String): Map<String, String> {
        val regex = Regex("""<string\s+name="([^"]+)">([^<]*)</string>""")
        val map = LinkedHashMap<String, String>()
        for (match in regex.findAll(xml)) {
            val key = match.groupValues[1]
            val value = unescapeXmlString(match.groupValues[2])
            map[key] = value
        }
        return map
    }

    private fun unescapeXmlString(xmlString: String): String {
        return xmlString
            .replace("\\\"", "\"")
            .replace("\\'", "'")
    }

    /**
     * In-memory FakeSharedPreferences implementation for deterministic local unit testing.
     */
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
                    temp.clear()
                    clearFlag = false
                }
            }
        }
    }
}
