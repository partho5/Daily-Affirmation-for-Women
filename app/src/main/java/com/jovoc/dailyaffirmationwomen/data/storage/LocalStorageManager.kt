package com.jovoc.dailyaffirmationwomen.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.jovoc.dailyaffirmationwomen.data.model.Level
import com.jovoc.dailyaffirmationwomen.data.model.SessionLogEntry
import com.jovoc.dailyaffirmationwomen.data.model.UserSentence
import com.jovoc.dailyaffirmationwomen.data.model.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Thread-safe local storage manager and reactive app state engine.
 *
 * Implements the local storage contract defined in AGENT_INSTRUCTIONS.md Section 8.
 * Backed by Android SharedPreferences for persistence and Kotlin StateFlow for instant,
 * zero-lag Compose reactivity.
 *
 * CRITICAL PRIVACY & ARCHITECTURE RULES:
 * - Uses ONLY built-in org.json; zero external library bloat.
 * - Ephemeral answer box text is strictly NEVER persisted, logged, or cached.
 * - Session log retains at most 60 entries (FIFO: oldest entries pruned when capacity exceeded).
 * - Session log entries strictly NEVER contain user answer text.
 * - Custom sentence IDs always begin with the `u_` prefix.
 * - Custom sentence text is strictly validated to <= 200 characters.
 */
class LocalStorageManager(private val sharedPreferences: SharedPreferences) {

    private val lock = Any()

    // 1. Settings state
    private val _settings = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<UserSettings> = _settings.asStateFlow()

    // 2. Category level state: categoryId -> Level (gentle, medium, strong)
    private val _categoryLevels = MutableStateFlow(loadCategoryLevels())
    val categoryLevelsFlow: StateFlow<Map<String, Level>> = _categoryLevels.asStateFlow()

    // 3. Not yet streak: categoryId -> consecutive "Not yet" count
    private val _notYetStreak = MutableStateFlow(loadNotYetStreak())
    val notYetStreakFlow: StateFlow<Map<String, Int>> = _notYetStreak.asStateFlow()

    // 4. Favorites state: set of sentence IDs
    private val _favorites = MutableStateFlow(loadFavorites())
    val favoritesFlow: StateFlow<Set<String>> = _favorites.asStateFlow()

    // 5. User-created sentences: list of UserSentence (IDs prefixed with u_)
    private val _userSentences = MutableStateFlow(loadUserSentences())
    val userSentencesFlow: StateFlow<List<UserSentence>> = _userSentences.asStateFlow()

    // 6. Session log: max 60 entries of { date, categoryId, sentenceIds }
    private val _sessionLog = MutableStateFlow(loadSessionLog())
    val sessionLogFlow: StateFlow<List<SessionLogEntry>> = _sessionLog.asStateFlow()

    // 7. Opened categories: set of category IDs visited by the user
    private val _openedCategories = MutableStateFlow(loadOpenedCategories())
    val openedCategoriesFlow: StateFlow<Set<String>> = _openedCategories.asStateFlow()

    // ------------------------------------------------------------------------
    // Initialization & Loaders
    // ------------------------------------------------------------------------

    private fun loadSettings(): UserSettings {
        val raw = sharedPreferences.getString(KEY_SETTINGS, null)
        return parseSettings(raw)
    }

    private fun loadCategoryLevels(): Map<String, Level> {
        val raw = sharedPreferences.getString(KEY_CATEGORY_LEVEL, null)
        return parseCategoryLevels(raw)
    }

    private fun loadNotYetStreak(): Map<String, Int> {
        val raw = sharedPreferences.getString(KEY_NOT_YET_STREAK, null)
        return parseNotYetStreak(raw)
    }

    private fun loadFavorites(): Set<String> {
        val raw = sharedPreferences.getString(KEY_FAVORITES, null)
        return parseFavorites(raw)
    }

    private fun loadUserSentences(): List<UserSentence> {
        val raw = sharedPreferences.getString(KEY_USER_SENTENCES, null)
        return parseUserSentences(raw)
    }

    private fun loadSessionLog(): List<SessionLogEntry> {
        val raw = sharedPreferences.getString(KEY_SESSION_LOG, null)
        return parseSessionLog(raw)
    }

    private fun loadOpenedCategories(): Set<String> {
        val raw = sharedPreferences.getString(KEY_OPENED_CATEGORIES, null)
        return parseOpenedCategories(raw)
    }

    // ------------------------------------------------------------------------
    // Settings Operations
    // ------------------------------------------------------------------------

    fun getSettings(): UserSettings = _settings.value

    fun saveSettings(settings: UserSettings) {
        synchronized(lock) {
            _settings.value = settings
            sharedPreferences.edit()
                .putString(KEY_SETTINGS, serializeSettings(settings))
                .apply()
        }
    }

    fun setName(name: String?) {
        synchronized(lock) {
            val current = _settings.value
            val formattedName = name?.trim()?.ifBlank { null }
            val updated = current.copy(name = formattedName)
            _settings.value = updated
            sharedPreferences.edit()
                .putString(KEY_SETTINGS, serializeSettings(updated))
                .apply()
        }
    }

    fun setOnboardingCompleted(
        answer: String?,
        startingLevel: Level,
        categoryIds: List<String> = emptyList()
    ) {
        synchronized(lock) {
            val current = _settings.value
            val formattedAnswer = answer?.trim()?.ifBlank { null }
            val updatedSettings = current.copy(
                onboardingAnswer = formattedAnswer,
                howItWorksSeen = true
            )
            _settings.value = updatedSettings

            val targetIds = if (categoryIds.isNotEmpty()) {
                categoryIds
            } else if (_categoryLevels.value.isNotEmpty()) {
                _categoryLevels.value.keys.toList()
            } else {
                DEFAULT_CATEGORY_IDS
            }

            val updatedLevels = _categoryLevels.value.toMutableMap()
            for (catId in targetIds) {
                updatedLevels[catId] = startingLevel
            }
            _categoryLevels.value = updatedLevels

            sharedPreferences.edit()
                .putString(KEY_SETTINGS, serializeSettings(updatedSettings))
                .putString(KEY_CATEGORY_LEVEL, serializeCategoryLevels(updatedLevels))
                .apply()
        }
    }

    // ------------------------------------------------------------------------
    // Category Level Operations
    // ------------------------------------------------------------------------

    fun getCategoryLevel(categoryId: String, defaultLevel: Level = Level.GENTLE): Level {
        return _categoryLevels.value[categoryId] ?: defaultLevel
    }

    fun setCategoryLevel(categoryId: String, level: Level) {
        synchronized(lock) {
            val updated = _categoryLevels.value.toMutableMap()
            updated[categoryId] = level
            _categoryLevels.value = updated
            sharedPreferences.edit()
                .putString(KEY_CATEGORY_LEVEL, serializeCategoryLevels(updated))
                .apply()
        }
    }

    // ------------------------------------------------------------------------
    // Not Yet Streak Operations
    // ------------------------------------------------------------------------

    fun getNotYetStreak(categoryId: String): Int {
        return _notYetStreak.value[categoryId] ?: 0
    }

    fun incrementNotYetStreak(categoryId: String): Int {
        synchronized(lock) {
            val current = getNotYetStreak(categoryId)
            val next = current + 1
            val updated = _notYetStreak.value.toMutableMap()
            updated[categoryId] = next
            _notYetStreak.value = updated
            sharedPreferences.edit()
                .putString(KEY_NOT_YET_STREAK, serializeNotYetStreak(updated))
                .apply()
            return next
        }
    }

    fun resetNotYetStreak(categoryId: String) {
        synchronized(lock) {
            val updated = _notYetStreak.value.toMutableMap()
            updated[categoryId] = 0
            _notYetStreak.value = updated
            sharedPreferences.edit()
                .putString(KEY_NOT_YET_STREAK, serializeNotYetStreak(updated))
                .apply()
        }
    }

    // ------------------------------------------------------------------------
    // Favorites Operations
    // ------------------------------------------------------------------------

    fun isFavorite(sentenceId: String): Boolean {
        return _favorites.value.contains(sentenceId)
    }

    fun toggleFavorite(sentenceId: String): Boolean {
        synchronized(lock) {
            val current = _favorites.value
            val isFav = current.contains(sentenceId)
            val updated = if (isFav) {
                current - sentenceId
            } else {
                current + sentenceId
            }
            _favorites.value = updated
            sharedPreferences.edit()
                .putString(KEY_FAVORITES, serializeFavorites(updated))
                .apply()
            return !isFav
        }
    }

    fun getFavorites(): Set<String> {
        return _favorites.value
    }

    // ------------------------------------------------------------------------
    // User-Created Sentences Operations
    // ------------------------------------------------------------------------

    fun getUserSentences(): List<UserSentence> = _userSentences.value

    fun getUserSentencesForCategory(categoryId: String): List<UserSentence> {
        return _userSentences.value.filter { it.categoryId == categoryId }
    }

    fun saveUserSentence(categoryId: String, text: String): UserSentence {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("User sentence text cannot be blank")
        }
        if (trimmed.length > MAX_SENTENCE_LENGTH) {
            throw IllegalArgumentException("User sentence text must not exceed $MAX_SENTENCE_LENGTH characters (was ${trimmed.length})")
        }

        val newSentence = UserSentence(
            id = UserSentence.generateId(),
            categoryId = categoryId.trim(),
            text = trimmed,
            createdAt = System.currentTimeMillis()
        )

        synchronized(lock) {
            val updated = _userSentences.value + newSentence
            _userSentences.value = updated
            sharedPreferences.edit()
                .putString(KEY_USER_SENTENCES, serializeUserSentences(updated))
                .apply()
        }
        return newSentence
    }

    fun updateUserSentence(sentenceId: String, text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_SENTENCE_LENGTH) {
            return false
        }

        synchronized(lock) {
            val current = _userSentences.value
            val index = current.indexOfFirst { it.id == sentenceId }
            if (index == -1) return false

            val updated = current.toMutableList()
            val existing = updated[index]
            updated[index] = existing.copy(text = trimmed)

            _userSentences.value = updated
            sharedPreferences.edit()
                .putString(KEY_USER_SENTENCES, serializeUserSentences(updated))
                .apply()
            return true
        }
    }

    fun deleteUserSentence(sentenceId: String): Boolean {
        synchronized(lock) {
            val current = _userSentences.value
            val exists = current.any { it.id == sentenceId }
            if (!exists) return false

            val updated = current.filterNot { it.id == sentenceId }
            _userSentences.value = updated
            val editor = sharedPreferences.edit()
                .putString(KEY_USER_SENTENCES, serializeUserSentences(updated))

            if (_favorites.value.contains(sentenceId)) {
                val updatedFavorites = _favorites.value - sentenceId
                _favorites.value = updatedFavorites
                editor.putString(KEY_FAVORITES, serializeFavorites(updatedFavorites))
            }

            editor.apply()
            return true
        }
    }

    // ------------------------------------------------------------------------
    // Session Log Operations
    // ------------------------------------------------------------------------

    fun recordSession(
        categoryId: String,
        sentenceIds: List<String>,
        localDate: String = getTodayLocalDateString()
    ) {
        synchronized(lock) {
            val newEntry = SessionLogEntry(
                date = localDate,
                categoryId = categoryId.trim(),
                sentenceIds = sentenceIds.toList()
            )
            val current = _sessionLog.value
            val updated = ArrayList<SessionLogEntry>(MAX_SESSION_LOG_SIZE)
            // Prepend new entry (newest first)
            updated.add(newEntry)
            for (entry in current) {
                if (updated.size < MAX_SESSION_LOG_SIZE) {
                    updated.add(entry)
                } else {
                    break // FIFO: discard oldest entries exceeding max capacity
                }
            }
            _sessionLog.value = updated
            sharedPreferences.edit()
                .putString(KEY_SESSION_LOG, serializeSessionLog(updated))
                .apply()
        }
    }

    fun getSessionLog(): List<SessionLogEntry> = _sessionLog.value

    fun hasCompletedSessionToday(todayDateStr: String = getTodayLocalDateString()): Boolean {
        return _sessionLog.value.any { it.date == todayDateStr }
    }

    // ------------------------------------------------------------------------
    // Opened Categories Operations
    // ------------------------------------------------------------------------

    fun isCategoryOpened(categoryId: String): Boolean {
        return _openedCategories.value.contains(categoryId)
    }

    fun markCategoryOpened(categoryId: String) {
        synchronized(lock) {
            if (!_openedCategories.value.contains(categoryId)) {
                val updated = _openedCategories.value + categoryId
                _openedCategories.value = updated
                sharedPreferences.edit()
                    .putString(KEY_OPENED_CATEGORIES, serializeOpenedCategories(updated))
                    .apply()
            }
        }
    }

    // ------------------------------------------------------------------------
    // Complete Data Reset
    // ------------------------------------------------------------------------

    fun deleteAllData() {
        synchronized(lock) {
            sharedPreferences.edit().clear().commit()
            _settings.value = UserSettings()
            _categoryLevels.value = emptyMap()
            _notYetStreak.value = emptyMap()
            _favorites.value = emptySet()
            _userSentences.value = emptyList()
            _sessionLog.value = emptyList()
            _openedCategories.value = emptySet()
        }
    }

    companion object {
        const val PREFS_NAME = "daily_affirmation_storage"
        const val MAX_SESSION_LOG_SIZE = 60
        const val MAX_SENTENCE_LENGTH = 200

        // Storage keys strictly matching AGENT_INSTRUCTIONS.md Section 8 table
        const val KEY_SETTINGS = "settings"
        const val KEY_CATEGORY_LEVEL = "categoryLevel"
        const val KEY_NOT_YET_STREAK = "notYetStreak"
        const val KEY_FAVORITES = "favorites"
        const val KEY_USER_SENTENCES = "userSentences"
        const val KEY_SESSION_LOG = "sessionLog"
        const val KEY_OPENED_CATEGORIES = "openedCategories"

        val DEFAULT_CATEGORY_IDS = listOf(
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

        @Volatile
        private var instance: LocalStorageManager? = null

        fun getInstance(context: Context): LocalStorageManager {
            return instance ?: synchronized(this) {
                instance ?: LocalStorageManager(
                    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
        }

        fun getTodayLocalDateString(): String {
            return LocalDate.now().toString()
        }

        // --------------------------------------------------------------------
        // JSON Serialization & Deserialization Helpers (org.json built-in)
        // --------------------------------------------------------------------

        fun parseSettings(jsonStr: String?): UserSettings {
            if (jsonStr.isNullOrBlank()) return UserSettings()
            return try {
                UserSettings.fromJsonObject(JSONObject(jsonStr))
            } catch (_: Exception) {
                UserSettings()
            }
        }

        fun serializeSettings(settings: UserSettings): String {
            return settings.toJsonObject().toString()
        }

        fun parseCategoryLevels(jsonStr: String?): Map<String, Level> {
            if (jsonStr.isNullOrBlank()) return emptyMap()
            return try {
                val obj = JSONObject(jsonStr)
                val map = LinkedHashMap<String, Level>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val levelStr = obj.optString(key)
                    val level = Level.fromKey(levelStr) ?: Level.fromString(levelStr)
                    map[key] = level
                }
                map
            } catch (_: Exception) {
                emptyMap()
            }
        }

        fun serializeCategoryLevels(levels: Map<String, Level>): String {
            val obj = JSONObject()
            for ((k, v) in levels) {
                obj.put(k, v.toKey())
            }
            return obj.toString()
        }

        fun parseNotYetStreak(jsonStr: String?): Map<String, Int> {
            if (jsonStr.isNullOrBlank()) return emptyMap()
            return try {
                val obj = JSONObject(jsonStr)
                val map = LinkedHashMap<String, Int>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = obj.optInt(key, 0)
                }
                map
            } catch (_: Exception) {
                emptyMap()
            }
        }

        fun serializeNotYetStreak(streaks: Map<String, Int>): String {
            val obj = JSONObject()
            for ((k, v) in streaks) {
                obj.put(k, v)
            }
            return obj.toString()
        }

        fun parseFavorites(jsonStr: String?): Set<String> {
            if (jsonStr.isNullOrBlank()) return emptySet()
            return try {
                val arr = JSONArray(jsonStr)
                val set = LinkedHashSet<String>(arr.length())
                for (i in 0 until arr.length()) {
                    val id = arr.optString(i).trim()
                    if (id.isNotEmpty()) {
                        set.add(id)
                    }
                }
                set
            } catch (_: Exception) {
                emptySet()
            }
        }

        fun serializeFavorites(favorites: Set<String>): String {
            val arr = JSONArray()
            for (id in favorites) {
                arr.put(id)
            }
            return arr.toString()
        }

        fun parseUserSentences(jsonStr: String?): List<UserSentence> {
            if (jsonStr.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(jsonStr)
                val list = ArrayList<UserSentence>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    try {
                        list.add(UserSentence.fromJsonObject(obj))
                    } catch (_: Exception) {
                        // Skip corrupted entry
                    }
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun serializeUserSentences(sentences: List<UserSentence>): String {
            val arr = JSONArray()
            for (sentence in sentences) {
                arr.put(sentence.toJsonObject())
            }
            return arr.toString()
        }

        fun parseSessionLog(jsonStr: String?): List<SessionLogEntry> {
            if (jsonStr.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(jsonStr)
                val list = ArrayList<SessionLogEntry>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    try {
                        list.add(SessionLogEntry.fromJsonObject(obj))
                    } catch (_: Exception) {
                        // Skip corrupted entry
                    }
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun serializeSessionLog(logEntries: List<SessionLogEntry>): String {
            val arr = JSONArray()
            for (entry in logEntries) {
                arr.put(entry.toJsonObject())
            }
            return arr.toString()
        }

        fun parseOpenedCategories(jsonStr: String?): Set<String> {
            if (jsonStr.isNullOrBlank()) return emptySet()
            return try {
                val arr = JSONArray(jsonStr)
                val set = LinkedHashSet<String>(arr.length())
                for (i in 0 until arr.length()) {
                    val id = arr.optString(i).trim()
                    if (id.isNotEmpty()) {
                        set.add(id)
                    }
                }
                set
            } catch (_: Exception) {
                emptySet()
            }
        }

        fun serializeOpenedCategories(opened: Set<String>): String {
            val arr = JSONArray()
            for (id in opened) {
                arr.put(id)
            }
            return arr.toString()
        }
    }
}
