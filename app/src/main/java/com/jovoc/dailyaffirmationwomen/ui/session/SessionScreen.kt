package com.jovoc.dailyaffirmationwomen.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jovoc.dailyaffirmationwomen.R
import com.jovoc.dailyaffirmationwomen.data.model.Affirmation
import com.jovoc.dailyaffirmationwomen.data.model.DatasetState
import com.jovoc.dailyaffirmationwomen.data.model.Level
import com.jovoc.dailyaffirmationwomen.data.model.SessionLogEntry
import com.jovoc.dailyaffirmationwomen.data.model.SessionMode
import com.jovoc.dailyaffirmationwomen.data.model.UserSentence
import com.jovoc.dailyaffirmationwomen.data.repository.DatasetRepository
import com.jovoc.dailyaffirmationwomen.data.storage.LocalStorageManager
import com.jovoc.dailyaffirmationwomen.domain.SentenceSelector
import com.jovoc.dailyaffirmationwomen.ui.components.CloseButton
import com.jovoc.dailyaffirmationwomen.ui.components.DatasetErrorCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The 4 practice session steps.
 */
enum class SessionStep {
    READ,   // Screen A
    CHECK,  // Screen B
    DO,     // Screen C
    CLOSE   // Screen D
}

/**
 * Standard Guided Practice Session & Level Adaptation (Feature F4, Section 3.2).
 *
 * Implements:
 * - Full-screen modal with small accessible CloseButton (>= 48x48 dp) at top.
 * - Simple crossfade transitions (< 250ms) between steps respecting system reduced motion.
 * - Standard flow: Screen A (Read) -> Screen B (Check) -> Screen C (Do) -> Screen D (Close).
 * - User's own sentence flow: Screen A (Read) -> Screen D (Close) directly (bypasses Check, Do, and level change).
 * - Tracks practiced sentence IDs in this session.
 * - On `another_sentence`: picks next unpracticed affirmation via SentenceSelector, clears ephemeral text,
 *   loops back to Screen A.
 * - On finish/close: if at least 1 sentence was completed, records entry in sessionLog via LocalStorageManager,
 *   clears ephemeral text, navigates back.
 * - STRICT PRIVACY RULE: Ephemeral answer box text is held purely in-memory and never persisted or logged.
 * - Zero streaks, zero scores, zero badges, zero confetti.
 * - Never show levels "gentle", "medium", "strong" to the user.
 */
@Composable
fun SessionScreen(
    categoryId: String,
    sentenceId: String?,
    storageManager: LocalStorageManager,
    repository: DatasetRepository,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSettings: (() -> Unit)? = null
) {
    val datasetState by repository.state.collectAsState()
    val sessionLog by storageManager.sessionLogFlow.collectAsState()
    val categoryLevels by storageManager.categoryLevelsFlow.collectAsState()
    val favorites by storageManager.favoritesFlow.collectAsState()
    val userSentences by storageManager.userSentencesFlow.collectAsState()
    val settings by storageManager.settingsFlow.collectAsState()

    // Reduced motion check (< 250ms fade transition requirement)
    val context = LocalContext.current
    val isReducedMotion = remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }
    val animationSpec = remember(isReducedMotion) {
        tween<Float>(durationMillis = if (isReducedMotion) 0 else 200)
    }

    when (val state = datasetState) {
        is DatasetState.Loading -> {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        CloseButton(onClose = onClose)
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                    }
                }
            }
        }

        is DatasetState.Error -> {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        CloseButton(onClose = onClose)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        DatasetErrorCard(
                            errorMessage = state.message,
                            onRetry = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    repository.loadDatasetResult()
                                }
                            }
                        )
                    }
                }
            }
        }

        is DatasetState.Success -> {
            val dataset = state.dataset
            val category = dataset.findCategoryById(categoryId)

            if (category == null) {
                Surface(
                    modifier = modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            CloseButton(onClose = onClose)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.dataset_error_desc),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                return
            }

            // Paper timer mode (bedtime_unload) strictly delegates to SleepTimerScreen
            if (category.sessionMode == SessionMode.PAPER_TIMER) {
                SleepTimerScreen(
                    categoryId = category.id,
                    storageManager = storageManager,
                    repository = repository,
                    onClose = onClose,
                    initialSentenceId = sentenceId,
                    onNavigateToSettings = onNavigateToSettings
                )
                return
            }

            val metaSession = dataset.metaSession
            val maxSentences = metaSession.maxSentencesPerSession
            val avoidDays = metaSession.avoidRepeatingSentenceDays
            val noFitThreshold = metaSession.noFitAfterNotYetInARow
            val todayDateStr = remember { LocalStorageManager.getTodayLocalDateString() }

            // State management for session execution
            var initialized by remember(category.id, sentenceId) { mutableStateOf(false) }
            var currentStep by remember { mutableStateOf(SessionStep.READ) }
            var isCurrentSentenceUserSentence by remember { mutableStateOf(false) }
            var currentUserSentence by remember { mutableStateOf<UserSentence?>(null) }
            var currentAffirmation by remember { mutableStateOf<Affirmation?>(null) }
            val practicedSentenceIds = remember { mutableStateListOf<String>() }

            // Ephemeral answer box text: strictly kept in-memory
            var ephemeralAnswerText by remember { mutableStateOf("") }

            if (!initialized) {
                val isUser = sentenceId?.startsWith(UserSentence.PREFIX) == true
                isCurrentSentenceUserSentence = isUser
                if (isUser) {
                    currentUserSentence = userSentences.firstOrNull { it.id == sentenceId }
                } else {
                    val aff = if (sentenceId != null) {
                        category.affirmations.firstOrNull { it.id == sentenceId }
                    } else null
                    currentAffirmation = aff ?: if (category.affirmations.isNotEmpty()) {
                        SentenceSelector.getNextSentence(
                            category = category,
                            sessionLog = sessionLog,
                            todayDateStr = todayDateStr,
                            avoidDays = avoidDays
                        )
                    } else null
                }
                initialized = true
            }

            // Session completion / finish handler
            fun finishOrCloseSession() {
                val completedIds = practicedSentenceIds.toList()
                ephemeralAnswerText = "" // strictly clear in-memory text
                if (completedIds.isNotEmpty()) {
                    storageManager.recordSession(
                        categoryId = category.id,
                        sentenceIds = completedIds,
                        localDate = todayDateStr
                    )
                }
                onClose()
            }

            // Handle hardware and gesture back
            BackHandler {
                finishOrCloseSession()
            }

            val currentSentenceId = if (isCurrentSentenceUserSentence) {
                currentUserSentence?.id
            } else {
                currentAffirmation?.id
            }

            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Modal Top Bar with accessible CloseButton (48x48 dp touch target)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CloseButton(onClose = { finishOrCloseSession() })
                    }

                    // Session step transitions (< 250ms crossfade)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Crossfade(
                            targetState = currentStep,
                            animationSpec = animationSpec,
                            label = "SessionStepTransition"
                        ) { step ->
                            when (step) {
                                SessionStep.READ -> {
                                    val currentLevel = categoryLevels[category.id] ?: Level.GENTLE
                                    val sentenceText = if (isCurrentSentenceUserSentence) {
                                        currentUserSentence?.text.orEmpty()
                                    } else {
                                        currentAffirmation?.textForLevel(currentLevel).orEmpty()
                                    }

                                    ScreenARead(
                                        sessionMode = category.sessionMode,
                                        sentenceText = sentenceText,
                                        isUserSentence = isCurrentSentenceUserSentence,
                                        userName = settings.name,
                                        onNext = {
                                            if (isCurrentSentenceUserSentence) {
                                                // User's own sentence: bypass Check, Do, and level adaptation (Screen A -> Screen D)
                                                currentUserSentence?.let { sentence ->
                                                    if (!practicedSentenceIds.contains(sentence.id)) {
                                                        practicedSentenceIds.add(sentence.id)
                                                    }
                                                }
                                                currentStep = SessionStep.CLOSE
                                            } else {
                                                // Standard flow: proceed to Screen B (Check)
                                                currentStep = SessionStep.CHECK
                                            }
                                        },
                                        onNavigateToSettings = {
                                            finishOrCloseSession()
                                            onNavigateToSettings?.invoke()
                                        }
                                    )
                                }

                                SessionStep.CHECK -> {
                                    ScreenBCheck(
                                        categoryId = category.id,
                                        storageManager = storageManager,
                                        onProceedToScreenC = {
                                            currentStep = SessionStep.DO
                                        },
                                        noFitThreshold = noFitThreshold
                                    )
                                }

                                SessionStep.DO -> {
                                    ScreenCDo(
                                        prompt = currentAffirmation?.prompt.orEmpty(),
                                        ephemeralText = ephemeralAnswerText,
                                        onEphemeralTextChange = { ephemeralAnswerText = it },
                                        onNext = {
                                            currentAffirmation?.let { aff ->
                                                if (!practicedSentenceIds.contains(aff.id)) {
                                                    practicedSentenceIds.add(aff.id)
                                                }
                                            }
                                            currentStep = SessionStep.CLOSE
                                        },
                                        onThinkInstead = {
                                            currentAffirmation?.let { aff ->
                                                if (!practicedSentenceIds.contains(aff.id)) {
                                                    practicedSentenceIds.add(aff.id)
                                                }
                                            }
                                            currentStep = SessionStep.CLOSE
                                        }
                                    )
                                }

                                SessionStep.CLOSE -> {
                                    val isFav = currentSentenceId != null && favorites.contains(currentSentenceId)

                                    ScreenDClose(
                                        isFavorite = isFav,
                                        onToggleFavorite = {
                                            currentSentenceId?.let { storageManager.toggleFavorite(it) }
                                        },
                                        sentencesCompletedCount = practicedSentenceIds.size,
                                        maxSentencesPerSession = maxSentences,
                                        canShowAnotherSentence = !isCurrentSentenceUserSentence && category.affirmations.isNotEmpty(),
                                        onAnotherSentence = {
                                            ephemeralAnswerText = ""
                                            val virtualLog = listOf(
                                                SessionLogEntry(
                                                    date = todayDateStr,
                                                    categoryId = category.id,
                                                    sentenceIds = practicedSentenceIds.toList()
                                                )
                                            ) + sessionLog

                                            val nextAffirmation = SentenceSelector.getNextSentence(
                                                category = category,
                                                sessionLog = virtualLog,
                                                todayDateStr = todayDateStr,
                                                avoidDays = avoidDays
                                            )
                                            currentAffirmation = nextAffirmation
                                            isCurrentSentenceUserSentence = false
                                            currentUserSentence = null
                                            currentStep = SessionStep.READ
                                        },
                                        onDone = {
                                            finishOrCloseSession()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
