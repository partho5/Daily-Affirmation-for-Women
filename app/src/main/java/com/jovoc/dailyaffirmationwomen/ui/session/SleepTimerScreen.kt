package com.jovoc.dailyaffirmationwomen.ui.session

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jovoc.dailyaffirmationwomen.R
import com.jovoc.dailyaffirmationwomen.data.model.Affirmation
import com.jovoc.dailyaffirmationwomen.data.model.DatasetState
import com.jovoc.dailyaffirmationwomen.data.model.Level
import com.jovoc.dailyaffirmationwomen.data.model.SessionMode
import com.jovoc.dailyaffirmationwomen.data.repository.DatasetRepository
import com.jovoc.dailyaffirmationwomen.data.storage.LocalStorageManager
import com.jovoc.dailyaffirmationwomen.domain.SentenceSelector
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationButton
import com.jovoc.dailyaffirmationwomen.ui.components.CloseButton
import com.jovoc.dailyaffirmationwomen.ui.components.DatasetErrorCard
import com.jovoc.dailyaffirmationwomen.ui.theme.SleepTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Steps for the Sleep Timer practice session.
 */
enum class SleepSessionStep {
    READ,       // Screen A: Read affirmation
    CHECK,      // Screen B: Check question (Feels true / Not yet)
    PREPARE,    // Screen C variant: Instruction + "Start five minutes" button (NO answer box)
    COUNTDOWN,  // Dim dark countdown timer
    FINISHED    // Time is up: "Time is up. Good night."
}

/**
 * Helper object for sleep mode single-shot haptic vibration.
 *
 * Source of truth: AGENT_INSTRUCTIONS.md Section 3.3 & Section 6:
 * - Vibrate once when timer reaches 0.
 * - STRICT PROHIBITION: Zero audio/sound playback.
 */
object SleepVibrationHelper {
    const val VIBRATION_DURATION_MS = 500L

    fun triggerVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(VIBRATION_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(VIBRATION_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(VIBRATION_DURATION_MS)
                }
            }
        } catch (_: Exception) {
            // Defensive: safe fallback if vibration hardware unavailable or restricted
        }
    }
}

/**
 * Sleep Timer Practice Screen (`paper_timer`, category `bedtime_unload`).
 *
 * Source of truth: AGENT_INSTRUCTIONS.md Section 3.3, Section 5 & Section 6:
 * - Runs Screens A and B as normal using ScreenARead and ScreenBCheck.
 * - Screen C becomes: `sleep_instruction` ("Put your phone down. Write on paper for five minutes. Be specific."),
 *   and button `sleep_start` ("Start five minutes").
 * - STRICT PROHIBITION: NO answer box. The writing is on paper.
 * - On tapping start:
 *   - Wrapped in SleepTheme (dim dark background #0A0C0D with low-glare subdued typography).
 *   - Counts down from `category.sessionSeconds ?: 300` (5 minutes / 300s) tick-by-tick.
 *   - When time ends (reaches 0):
 *     - Triggers single haptic vibration (500ms).
 *     - Displays `sleep_end` ("Time is up. Good night.").
 *     - STRICT PROHIBITIONS: Absolutely NO sound! NO further sentences (NO Screen D).
 * - Records session in `sessionLog` on completion/exit.
 * - Small accessible close button at top.
 */
@Composable
fun SleepTimerScreen(
    categoryId: String,
    storageManager: LocalStorageManager,
    repository: DatasetRepository,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    initialSentenceId: String? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    onVibrate: (Context) -> Unit = { SleepVibrationHelper.triggerVibration(it) }
) {
    val datasetState by repository.state.collectAsState()
    val sessionLog by storageManager.sessionLogFlow.collectAsState()
    val categoryLevels by storageManager.categoryLevelsFlow.collectAsState()
    val settings by storageManager.settingsFlow.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Reduced motion check (< 250ms fade requirement)
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

    SleepTheme {
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
                            CloseButton(
                                onClose = onClose,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                            CloseButton(
                                onClose = onClose,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                                    coroutineScope.launch {
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
                                CloseButton(
                                    onClose = onClose,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = stringResource(R.string.dataset_error_desc),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    return@SleepTheme
                }

                val metaSession = dataset.metaSession
                val avoidDays = metaSession.avoidRepeatingSentenceDays
                val noFitThreshold = metaSession.noFitAfterNotYetInARow
                val totalDurationSeconds = category.sessionSeconds ?: 300
                val todayDateStr = remember { LocalStorageManager.getTodayLocalDateString() }

                // Affirmation selection
                val currentAffirmation = remember(category.id, initialSentenceId) {
                    val aff = if (initialSentenceId != null) {
                        category.affirmations.firstOrNull { it.id == initialSentenceId }
                    } else null
                    aff ?: if (category.affirmations.isNotEmpty()) {
                        SentenceSelector.getNextSentence(
                            category = category,
                            sessionLog = sessionLog,
                            todayDateStr = todayDateStr,
                            avoidDays = avoidDays
                        )
                    } else null
                }

                var currentStep by remember { mutableStateOf(SleepSessionStep.READ) }
                var remainingSeconds by remember { mutableIntStateOf(totalDurationSeconds) }
                var isTimerRunning by remember { mutableStateOf(false) }
                var hasVibrated by remember { mutableStateOf(false) }
                var sessionRecorded by remember { mutableStateOf(false) }

                fun recordSessionOnce() {
                    if (!sessionRecorded) {
                        currentAffirmation?.id?.let { sentenceId ->
                            storageManager.recordSession(
                                categoryId = category.id,
                                sentenceIds = listOf(sentenceId),
                                localDate = todayDateStr
                            )
                        }
                        sessionRecorded = true
                    }
                }

                fun finishOrClose() {
                    if (isTimerRunning || currentStep == SleepSessionStep.FINISHED || remainingSeconds <= 0) {
                        recordSessionOnce()
                    }
                    onClose()
                }

                // Intercept hardware and gesture back
                BackHandler {
                    finishOrClose()
                }

                // Tick-by-tick countdown coroutine
                LaunchedEffect(isTimerRunning) {
                    if (isTimerRunning) {
                        while (remainingSeconds > 0) {
                            delay(1000L)
                            remainingSeconds--
                        }
                    }
                }

                // Completion trigger when time reaches 0
                LaunchedEffect(remainingSeconds, isTimerRunning) {
                    if (isTimerRunning && remainingSeconds <= 0) {
                        isTimerRunning = false
                        currentStep = SleepSessionStep.FINISHED
                        if (!hasVibrated) {
                            hasVibrated = true
                            onVibrate(context)
                        }
                        recordSessionOnce()
                    }
                }

                Surface(
                    modifier = modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top bar with small accessible CloseButton (>= 48x48 dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CloseButton(
                                onClose = { finishOrClose() },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Session transitions (< 250ms crossfade)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Crossfade(
                                targetState = currentStep,
                                animationSpec = animationSpec,
                                label = "SleepStepTransition"
                            ) { step ->
                                when (step) {
                                    SleepSessionStep.READ -> {
                                        val currentLevel = categoryLevels[category.id] ?: Level.GENTLE
                                        val sentenceText = currentAffirmation?.textForLevel(currentLevel).orEmpty()

                                        ScreenARead(
                                            sessionMode = SessionMode.PAPER_TIMER,
                                            sentenceText = sentenceText,
                                            isUserSentence = false,
                                            userName = settings.name,
                                            onNext = {
                                                currentStep = SleepSessionStep.CHECK
                                            },
                                            onNavigateToSettings = {
                                                finishOrClose()
                                                onNavigateToSettings?.invoke()
                                            }
                                        )
                                    }

                                    SleepSessionStep.CHECK -> {
                                        ScreenBCheck(
                                            categoryId = category.id,
                                            storageManager = storageManager,
                                            onProceedToScreenC = {
                                                currentStep = SleepSessionStep.PREPARE
                                            },
                                            noFitThreshold = noFitThreshold
                                        )
                                    }

                                    SleepSessionStep.PREPARE -> {
                                        // Screen C variant: Instruction + Start button (STRICTLY NO answer box)
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 24.dp, vertical = 16.dp),
                                            verticalArrangement = Arrangement.SpaceBetween,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Spacer(modifier = Modifier.height(16.dp))

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxWidth(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.sleep_instruction),
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }

                                            // Start five minutes button
                                            AffirmationButton(
                                                text = stringResource(R.string.sleep_start),
                                                onClick = {
                                                    isTimerRunning = true
                                                    currentStep = SleepSessionStep.COUNTDOWN
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }

                                    SleepSessionStep.COUNTDOWN -> {
                                        // Dim dark countdown timer
                                        val minutes = remainingSeconds / 60
                                        val seconds = remainingSeconds % 60
                                        val formattedTime = String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)

                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 24.dp, vertical = 16.dp),
                                            verticalArrangement = Arrangement.SpaceBetween,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = stringResource(R.string.sleep_instruction),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 16.dp)
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxWidth(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = formattedTime,
                                                    style = MaterialTheme.typography.displayLarge,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    textAlign = TextAlign.Center
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(48.dp))
                                        }
                                    }

                                    SleepSessionStep.FINISHED -> {
                                        // Time is up. Good night.
                                        // STRICT PROHIBITION: NO further sentences (NO Screen D), NO sound.
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 24.dp, vertical = 16.dp),
                                            verticalArrangement = Arrangement.SpaceBetween,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Spacer(modifier = Modifier.height(16.dp))

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxWidth(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.sleep_end),
                                                    style = MaterialTheme.typography.headlineMedium,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    textAlign = TextAlign.Center
                                                )
                                            }

                                            AffirmationButton(
                                                text = stringResource(R.string.done_button),
                                                onClick = { finishOrClose() },
                                                modifier = Modifier.fillMaxWidth()
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
    }
}
