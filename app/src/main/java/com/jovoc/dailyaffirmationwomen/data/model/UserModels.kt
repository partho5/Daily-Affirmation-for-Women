package com.jovoc.dailyaffirmationwomen.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * User-level application settings.
 *
 * CRITICAL PRIVACY & CONTRACT RULES:
 * - Ephemeral answer box text must NEVER be persisted or included here.
 * - [name] is optional and used solely to personalize affirmations containing `{name}`.
 * - [onboardingAnswer] holds the skippable starting-level response.
 * - [howItWorksSeen] tracks whether the 3 "How it works" cards were viewed.
 */
data class UserSettings(
    val name: String? = null,
    val onboardingAnswer: String? = null,
    val howItWorksSeen: Boolean = false
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        if (name != null) obj.put(KEY_NAME, name) else obj.put(KEY_NAME, JSONObject.NULL)
        if (onboardingAnswer != null) obj.put(KEY_ONBOARDING_ANSWER, onboardingAnswer) else obj.put(KEY_ONBOARDING_ANSWER, JSONObject.NULL)
        obj.put(KEY_HOW_IT_WORKS_SEEN, howItWorksSeen)
        return obj
    }

    companion object {
        const val KEY_NAME = "name"
        const val KEY_ONBOARDING_ANSWER = "onboardingAnswer"
        const val KEY_HOW_IT_WORKS_SEEN = "howItWorksSeen"

        fun fromJsonObject(obj: JSONObject): UserSettings {
            val name = if (obj.has(KEY_NAME) && !obj.isNull(KEY_NAME)) {
                obj.optString(KEY_NAME).trim().takeIf { it.isNotEmpty() }
            } else {
                null
            }
            val onboardingAnswer = if (obj.has(KEY_ONBOARDING_ANSWER) && !obj.isNull(KEY_ONBOARDING_ANSWER)) {
                obj.optString(KEY_ONBOARDING_ANSWER).trim().takeIf { it.isNotEmpty() }
            } else {
                null
            }
            val howItWorksSeen = obj.optBoolean(KEY_HOW_IT_WORKS_SEEN, false)

            return UserSettings(
                name = name,
                onboardingAnswer = onboardingAnswer,
                howItWorksSeen = howItWorksSeen
            )
        }
    }
}

/**
 * User-created affirmation sentence belonging to a specific category.
 *
 * Requirements:
 * - Sentence IDs must strictly begin with `u_` prefix to distinguish them from dataset IDs.
 * - Max length is 200 characters.
 * - Text is stored locally on device only and never sent to any server.
 */
data class UserSentence(
    val id: String,
    val categoryId: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.startsWith(PREFIX)) {
            "User sentence ID must start with '$PREFIX' prefix, got: $id"
        }
        require(text.length <= MAX_LENGTH) {
            "User sentence text must not exceed $MAX_LENGTH characters, was ${text.length}"
        }
    }

    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put(KEY_ID, id)
        obj.put(KEY_CATEGORY_ID, categoryId)
        obj.put(KEY_TEXT, text)
        obj.put(KEY_CREATED_AT, createdAt)
        return obj
    }

    companion object {
        const val PREFIX = "u_"
        const val MAX_LENGTH = 200

        const val KEY_ID = "id"
        const val KEY_CATEGORY_ID = "categoryId"
        const val KEY_TEXT = "text"
        const val KEY_CREATED_AT = "createdAt"

        /**
         * Generates a unique sentence ID that starts with `u_`.
         * Format: u_${timestamp}_${randomSuffix}
         */
        fun generateId(): String {
            val randomSuffix = UUID.randomUUID().toString().replace("-", "").take(6)
            return "${PREFIX}${System.currentTimeMillis()}_$randomSuffix"
        }

        fun fromJsonObject(obj: JSONObject): UserSentence {
            val id = obj.optString(KEY_ID)
            val categoryId = obj.optString(KEY_CATEGORY_ID)
            val text = obj.optString(KEY_TEXT)
            val createdAt = obj.optLong(KEY_CREATED_AT, System.currentTimeMillis())

            return UserSentence(
                id = id,
                categoryId = categoryId,
                text = text,
                createdAt = createdAt
            )
        }
    }
}

/**
 * Historical record of a completed practice session.
 *
 * CRITICAL PRIVACY & CONTRACT RULES:
 * - Date must be in YYYY-MM-DD local format.
 * - Strictly NO user answer text, thoughts, or responses may ever be stored.
 * - Maximum of 60 entries retained (FIFO prune oldest).
 * - Used for Today's practice suggestion rotation and daily completion check.
 */
data class SessionLogEntry(
    val date: String,
    val categoryId: String,
    val sentenceIds: List<String>
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put(KEY_DATE, date)
        obj.put(KEY_CATEGORY_ID, categoryId)
        val arr = JSONArray()
        for (sid in sentenceIds) {
            arr.put(sid)
        }
        obj.put(KEY_SENTENCE_IDS, arr)
        return obj
    }

    companion object {
        const val KEY_DATE = "date"
        const val KEY_CATEGORY_ID = "categoryId"
        const val KEY_SENTENCE_IDS = "sentenceIds"

        fun fromJsonObject(obj: JSONObject): SessionLogEntry {
            val date = obj.optString(KEY_DATE)
            val categoryId = obj.optString(KEY_CATEGORY_ID)
            val arr = obj.optJSONArray(KEY_SENTENCE_IDS)
            val sentenceIds = ArrayList<String>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val sid = arr.optString(i)
                    if (sid.isNotBlank()) {
                        sentenceIds.add(sid)
                    }
                }
            }
            return SessionLogEntry(
                date = date,
                categoryId = categoryId,
                sentenceIds = sentenceIds
            )
        }
    }
}
