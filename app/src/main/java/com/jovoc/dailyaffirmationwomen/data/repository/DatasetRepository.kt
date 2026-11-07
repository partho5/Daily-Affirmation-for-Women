package com.jovoc.dailyaffirmationwomen.data.repository

import android.content.Context
import com.jovoc.dailyaffirmationwomen.data.model.Affirmation
import com.jovoc.dailyaffirmationwomen.data.model.AffirmationDataset
import com.jovoc.dailyaffirmationwomen.data.model.Category
import com.jovoc.dailyaffirmationwomen.data.model.DatasetState
import com.jovoc.dailyaffirmationwomen.data.model.Level
import com.jovoc.dailyaffirmationwomen.data.model.MetaSession
import com.jovoc.dailyaffirmationwomen.data.model.ResearchNote
import com.jovoc.dailyaffirmationwomen.data.model.SessionMode
import com.jovoc.dailyaffirmationwomen.data.model.SuggestPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Thread-safe repository responsible for asynchronously loading, validating,
 * and caching the affirmation dataset from bundled assets.
 *
 * Uses only Android runtime built-in `org.json` for zero-overhead parsing.
 */
class DatasetRepository(context: Context) {

    private val appContext: Context = context.applicationContext

    private val _state = MutableStateFlow<DatasetState>(DatasetState.Loading)
    val state: StateFlow<DatasetState> = _state.asStateFlow()

    @Volatile
    private var cachedDataset: AffirmationDataset? = null

    private val loadMutex = Mutex()

    /**
     * Synchronous getter returning the cached dataset, or null if not yet loaded.
     */
    fun getDatasetOrNull(): AffirmationDataset? = cachedDataset

    /**
     * Pure parser instance delegation, enabling callers to parse from either
     * instance reference or static companion.
     */
    fun parseJson(jsonString: String): AffirmationDataset = Companion.parseJson(jsonString)

    /**
     * Asynchronously loads, parses, and validates the bundled dataset asset on Dispatchers.IO.
     * Caches the result in memory for subsequent instant synchronous reads.
     *
     * @throws IllegalStateException or IOException if reading or validation fails.
     */
    suspend fun loadDataset(): AffirmationDataset = withContext(Dispatchers.IO) {
        cachedDataset?.let { return@withContext it }

        loadMutex.withLock {
            cachedDataset?.let { return@withContext it }

            try {
                _state.value = DatasetState.Loading
                val jsonString = appContext.assets.open(ASSET_FILE_NAME).bufferedReader().use { reader ->
                    reader.readText()
                }
                val dataset = Companion.parseJson(jsonString)
                cachedDataset = dataset
                _state.value = DatasetState.Success(dataset)
                dataset
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Failed to load affirmation dataset"
                _state.value = DatasetState.Error(errorMsg)
                throw e
            }
        }
    }

    /**
     * Safe asynchronous load that catches exceptions and returns Kotlin [Result].
     */
    suspend fun loadDatasetResult(): Result<AffirmationDataset> = runCatching {
        loadDataset()
    }

    companion object {
        const val ASSET_FILE_NAME = "affirmation_dataset.json"

        @Volatile
        private var instance: DatasetRepository? = null

        /**
         * Singleton accessor using double-checked locking for lightweight manual DI.
         */
        fun getInstance(context: Context): DatasetRepository {
            return instance ?: synchronized(this) {
                instance ?: DatasetRepository(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Pure parser method parsing raw JSON string into [AffirmationDataset]
         * with strict structural validation.
         *
         * Can be called directly in unit tests without an Android Context.
         *
         * @throws IllegalStateException if required fields are missing or blank.
         */
        @Throws(IllegalStateException::class)
        fun parseJson(jsonString: String): AffirmationDataset {
            val rootObj = try {
                JSONObject(jsonString)
            } catch (e: Exception) {
                throw IllegalStateException("Invalid JSON structure in dataset: ${e.message}", e)
            }

            // 1. Parse meta & session rules
            val metaObj = rootObj.optJSONObject("meta")
            val namePlaceholder = metaObj?.optStringOrNull("name_placeholder") ?: "{name}"

            val sessionObj = metaObj?.optJSONObject("session")
            val metaSession = if (sessionObj != null) {
                MetaSession(
                    standardTargetSeconds = sessionObj.optInt("standard_target_seconds", 150),
                    maxSentencesPerSession = sessionObj.optInt("max_sentences_per_session", 3),
                    nightStartHour = sessionObj.optInt("night_start_hour", 21),
                    nightEndHour = sessionObj.optInt("night_end_hour", 5),
                    limitedCategoryMinDaysBetweenSuggestions = sessionObj.optInt("limited_category_min_days_between_suggestions", 3),
                    noFitAfterNotYetInARow = sessionObj.optInt("no_fit_after_not_yet_in_a_row", 3),
                    avoidRepeatingSentenceDays = sessionObj.optInt("avoid_repeating_sentence_days", 7),
                    answerBoxSaved = sessionObj.optBoolean("answer_box_saved", false)
                )
            } else {
                MetaSession()
            }

            // 2. Parse categories
            val categoriesArray = rootObj.optJSONArray("categories")
                ?: throw IllegalStateException("Dataset JSON is missing required 'categories' array")

            if (categoriesArray.length() == 0) {
                throw IllegalStateException("Dataset 'categories' array must not be empty")
            }

            val categories = ArrayList<Category>(categoriesArray.length())
            for (i in 0 until categoriesArray.length()) {
                val catObj = categoriesArray.optJSONObject(i)
                    ?: throw IllegalStateException("Category at index $i is not a valid JSON object")

                val catId = catObj.optString("id").trim()
                if (catId.isEmpty()) {
                    throw IllegalStateException("Category at index $i is missing required 'id'")
                }

                val title = catObj.optString("title").trim()
                if (title.isEmpty()) {
                    throw IllegalStateException("Category '$catId' is missing required 'title'")
                }

                val whenToUse = catObj.optString("when_to_use", "").trim()
                val sessionMode = SessionMode.fromString(catObj.optStringOrNull("session_mode"))
                val suggestPolicy = SuggestPolicy.fromString(catObj.optStringOrNull("suggest_policy"))
                val sessionSeconds = catObj.optIntOrNull("session_seconds")
                val deepOptionSeconds = catObj.optIntOrNull("deep_option_seconds")
                val evidence = catObj.optStringOrNull("evidence")
                val researchNoteId = catObj.optStringOrNull("research_note_id")
                val audience = catObj.optStringOrNull("audience")

                val affirmationsArray = catObj.optJSONArray("affirmations")
                    ?: throw IllegalStateException("Category '$catId' is missing required 'affirmations' array")

                if (affirmationsArray.length() == 0) {
                    throw IllegalStateException("Category '$catId' must contain at least one affirmation")
                }

                val affirmations = ArrayList<Affirmation>(affirmationsArray.length())
                for (j in 0 until affirmationsArray.length()) {
                    val affObj = affirmationsArray.optJSONObject(j)
                        ?: throw IllegalStateException("Affirmation at index $j in category '$catId' is not a valid JSON object")

                    val affId = affObj.optString("id").trim()
                    if (affId.isEmpty()) {
                        throw IllegalStateException("Affirmation at index $j in category '$catId' is missing required 'id'")
                    }

                    val levelsObj = affObj.optJSONObject("levels")
                        ?: throw IllegalStateException("Affirmation '$affId' in category '$catId' is missing required 'levels' object")

                    val gentle = levelsObj.optStringOrNull("gentle")?.trim().orEmpty()
                    val medium = levelsObj.optStringOrNull("medium")?.trim().orEmpty()
                    val strong = levelsObj.optStringOrNull("strong")?.trim().orEmpty()

                    if (gentle.isEmpty()) {
                        throw IllegalStateException("Affirmation '$affId' in category '$catId' is missing required non-blank 'levels.gentle'")
                    }
                    if (medium.isEmpty()) {
                        throw IllegalStateException("Affirmation '$affId' in category '$catId' is missing required non-blank 'levels.medium'")
                    }
                    if (strong.isEmpty()) {
                        throw IllegalStateException("Affirmation '$affId' in category '$catId' is missing required non-blank 'levels.strong'")
                    }

                    val levelsMap = mapOf(
                        Level.GENTLE to gentle,
                        Level.MEDIUM to medium,
                        Level.STRONG to strong
                    )

                    val prompt = affObj.optString("prompt", "").trim()
                    val mechanism = affObj.optStringOrNull("mechanism")
                    val bestTime = affObj.optStringOrNull("best_time")

                    affirmations.add(
                        Affirmation(
                            id = affId,
                            levels = levelsMap,
                            prompt = prompt,
                            mechanism = mechanism,
                            bestTime = bestTime
                        )
                    )
                }

                categories.add(
                    Category(
                        id = catId,
                        title = title,
                        whenToUse = whenToUse,
                        sessionMode = sessionMode,
                        suggestPolicy = suggestPolicy,
                        sessionSeconds = sessionSeconds,
                        deepOptionSeconds = deepOptionSeconds,
                        evidence = evidence,
                        researchNoteId = researchNoteId,
                        audience = audience,
                        affirmations = affirmations
                    )
                )
            }

            // 3. Parse research notes
            val researchNotesArray = rootObj.optJSONArray("research_notes")
            val researchNotes = LinkedHashMap<String, ResearchNote>()
            if (researchNotesArray != null) {
                for (i in 0 until researchNotesArray.length()) {
                    val noteObj = researchNotesArray.optJSONObject(i) ?: continue
                    val noteId = noteObj.optString("id").trim()
                    if (noteId.isNotEmpty()) {
                        val claimUi = noteObj.optString("claim_ui", "").trim()
                        val population = noteObj.optString("population", "").trim()
                        val source = noteObj.optString("source", "").trim()
                        researchNotes[noteId] = ResearchNote(
                            id = noteId,
                            claimUi = claimUi,
                            population = population,
                            source = source
                        )
                    }
                }
            }

            return AffirmationDataset(
                metaSession = metaSession,
                namePlaceholder = namePlaceholder,
                categories = categories,
                researchNotes = researchNotes
            )
        }

        private fun JSONObject.optStringOrNull(name: String): String? {
            if (!has(name) || isNull(name)) return null
            val value = optString(name)
            return if (value.equals("null", ignoreCase = true) || value.isEmpty()) null else value
        }

        private fun JSONObject.optIntOrNull(name: String): Int? {
            if (!has(name) || isNull(name)) return null
            return optInt(name)
        }
    }
}
