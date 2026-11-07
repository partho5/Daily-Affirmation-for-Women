package com.jovoc.dailyaffirmationwomen.data.model

/**
 * Three practice levels for affirmations.
 *
 * CRITICAL RULE: The words "gentle", "medium", and "strong" must NEVER
 * be displayed to the user in any UI element.
 */
enum class Level(val key: String) {
    GENTLE("gentle"),
    MEDIUM("medium"),
    STRONG("strong");

    fun toKey(): String = key

    /**
     * Transition up when the user selects "Feels true" (capped at STRONG).
     */
    fun next(): Level = when (this) {
        GENTLE -> MEDIUM
        MEDIUM -> STRONG
        STRONG -> STRONG
    }

    /**
     * Transition down when the user selects "Not yet" (capped at GENTLE).
     */
    fun previous(): Level = when (this) {
        STRONG -> MEDIUM
        MEDIUM -> GENTLE
        GENTLE -> GENTLE
    }

    companion object {
        fun fromString(value: String?): Level {
            return when (value?.trim()?.lowercase()) {
                "strong" -> STRONG
                "medium" -> MEDIUM
                "gentle" -> GENTLE
                else -> GENTLE
            }
        }

        fun fromKey(key: String?): Level? {
            return entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) }
        }
    }
}

/**
 * Session presentation modes supported by the practice.
 */
enum class SessionMode(val key: String) {
    STANDARD("standard"),
    SAY_ALOUD("say_aloud"),
    PAPER_TIMER("paper_timer");

    fun toKey(): String = key

    companion object {
        fun fromString(value: String?): SessionMode {
            return when (value?.trim()?.lowercase()) {
                "say_aloud" -> SAY_ALOUD
                "paper_timer" -> PAPER_TIMER
                "standard" -> STANDARD
                else -> STANDARD
            }
        }
    }
}

/**
 * Suggestion policies for Today's practice rotation.
 */
enum class SuggestPolicy(val key: String) {
    NORMAL("normal"),
    NIGHT_ONLY("night_only"),
    OPT_IN("opt_in"),
    LIMITED("limited");

    fun toKey(): String = key

    companion object {
        fun fromString(value: String?): SuggestPolicy {
            return when (value?.trim()?.lowercase()) {
                "night_only" -> NIGHT_ONLY
                "opt_in" -> OPT_IN
                "limited" -> LIMITED
                "normal" -> NORMAL
                else -> NORMAL
            }
        }
    }
}

/**
 * Practice limits and parameters read dynamically from meta.session.
 * Never hardcoded in practice screens.
 */
data class MetaSession(
    val standardTargetSeconds: Int = 150,
    val maxSentencesPerSession: Int = 3,
    val nightStartHour: Int = 21,
    val nightEndHour: Int = 5,
    val limitedCategoryMinDaysBetweenSuggestions: Int = 3,
    val noFitAfterNotYetInARow: Int = 3,
    val avoidRepeatingSentenceDays: Int = 7,
    val answerBoxSaved: Boolean = false
)

/**
 * Built-in affirmation sentence with 3 levels and guided practice prompt.
 */
data class Affirmation(
    val id: String,
    val levels: Map<Level, String>,
    val prompt: String,
    val mechanism: String? = null,
    val bestTime: String? = null
) {
    /**
     * Resolves the sentence text for a specific level, falling back to gentle if missing.
     */
    fun textForLevel(level: Level): String {
        return levels[level] ?: levels[Level.GENTLE].orEmpty()
    }
}

/**
 * A practice category containing guided affirmations.
 */
data class Category(
    val id: String,
    val title: String,
    val whenToUse: String,
    val sessionMode: SessionMode = SessionMode.STANDARD,
    val suggestPolicy: SuggestPolicy = SuggestPolicy.NORMAL,
    val sessionSeconds: Int? = null,
    val deepOptionSeconds: Int? = null,
    val evidence: String? = null,
    val researchNoteId: String? = null,
    val audience: String? = null,
    val affirmations: List<Affirmation>
)

/**
 * Research note accompanying a category.
 * Only claimUi, population, and source are permitted to be shown to the user.
 */
data class ResearchNote(
    val id: String,
    val claimUi: String,
    val population: String,
    val source: String
)

/**
 * Complete immutable parsed affirmation dataset.
 */
data class AffirmationDataset(
    val metaSession: MetaSession,
    val namePlaceholder: String = "{name}",
    val categories: List<Category>,
    val researchNotes: Map<String, ResearchNote>
) {
    fun findCategoryById(id: String): Category? = categories.firstOrNull { it.id == id }

    fun findAffirmationById(id: String): Affirmation? {
        for (category in categories) {
            val found = category.affirmations.firstOrNull { it.id == id }
            if (found != null) return found
        }
        return null
    }
}

/**
 * UI loading and error state for dataset availability.
 */
sealed class DatasetState {
    data object Loading : DatasetState()
    data class Success(val dataset: AffirmationDataset) : DatasetState()
    data class Error(val message: String) : DatasetState()
}
