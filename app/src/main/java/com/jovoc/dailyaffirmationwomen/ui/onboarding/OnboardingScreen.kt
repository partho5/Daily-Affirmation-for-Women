package com.jovoc.dailyaffirmationwomen.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.jovoc.dailyaffirmationwomen.R
import com.jovoc.dailyaffirmationwomen.data.model.Level
import com.jovoc.dailyaffirmationwomen.data.repository.DatasetRepository
import com.jovoc.dailyaffirmationwomen.data.storage.LocalStorageManager
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationButton
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationCard
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationOutlinedButton
import com.jovoc.dailyaffirmationwomen.ui.components.BackButton

/**
 * Onboarding multi-step flow implementing Feature F1.
 * Source of truth: AGENT_INSTRUCTIONS.md Section 4 (F1), Section 5, Section 7.
 *
 * Requirements & Constraints:
 * - All steps are completely skippable.
 * - Step 1: Optional first name. Saved only to `settings.name`.
 * - Step 2: One question: "When you read a kind sentence about yourself, what usually happens?"
 *   - "I believe it" -> sets starting level to Level.MEDIUM
 *   - "I want to believe it" -> sets starting level to Level.GENTLE
 *   - "I doubt it" -> sets starting level to Level.GENTLE
 *   - Skipped -> sets starting level to Level.GENTLE
 *   - Applies to all categories via `LocalStorageManager.setOnboardingCompleted`
 *   - STRICT PROHIBITION: Never display level names ("gentle", "medium", "strong") to the user.
 * - Step 3: Three "How it works" cards using Section 5 strings verbatim, followed by "Start" button.
 * - On completion or skip: sets `howItWorksSeen = true` and navigates to Home.
 * - All interactive elements have touch targets >= 48x48 dp.
 */
enum class OnboardingStep {
    NAME,
    QUESTION,
    HOW_IT_WORKS
}

@Composable
fun OnboardingScreen(
    storageManager: LocalStorageManager,
    repository: DatasetRepository? = null,
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableStateOf(OnboardingStep.NAME) }
    var nameInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val categoryIds = remember(repository) {
        repository?.getDatasetOrNull()?.categories?.map { it.id }
            ?: LocalStorageManager.DEFAULT_CATEGORY_IDS
    }

    // Reduced motion support per Section 7 UI direction (< 250ms fade transitions)
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

    // Multi-step back navigation handling
    BackHandler(enabled = currentStep != OnboardingStep.NAME) {
        when (currentStep) {
            OnboardingStep.HOW_IT_WORKS -> currentStep = OnboardingStep.QUESTION
            OnboardingStep.QUESTION -> currentStep = OnboardingStep.NAME
            OnboardingStep.NAME -> { /* Handled by system */ }
        }
    }

    val onSkipAll: () -> Unit = {
        storageManager.setOnboardingCompleted(
            answer = null,
            startingLevel = Level.GENTLE,
            categoryIds = categoryIds
        )
        onFinishOnboarding()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep != OnboardingStep.NAME) {
                    BackButton(
                        onBack = {
                            when (currentStep) {
                                OnboardingStep.HOW_IT_WORKS -> currentStep = OnboardingStep.QUESTION
                                OnboardingStep.QUESTION -> currentStep = OnboardingStep.NAME
                                OnboardingStep.NAME -> { }
                            }
                        }
                    )
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                TextButton(
                    onClick = onSkipAll,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.skip_button),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = currentStep,
            animationSpec = animationSpec,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "OnboardingStepTransition"
        ) { step ->
            when (step) {
                OnboardingStep.NAME -> {
                    NameStep(
                        nameInput = nameInput,
                        onNameChange = { if (it.length <= 50) nameInput = it },
                        onNext = {
                            focusManager.clearFocus()
                            if (nameInput.isNotBlank()) {
                                storageManager.setName(nameInput.trim())
                            }
                            currentStep = OnboardingStep.QUESTION
                        },
                        onSkip = {
                            focusManager.clearFocus()
                            currentStep = OnboardingStep.QUESTION
                        }
                    )
                }
                OnboardingStep.QUESTION -> {
                    QuestionStep(
                        onOptionSelected = { optionText, startingLevel ->
                            storageManager.setOnboardingCompleted(
                                answer = optionText,
                                startingLevel = startingLevel,
                                categoryIds = categoryIds
                            )
                            currentStep = OnboardingStep.HOW_IT_WORKS
                        },
                        onSkip = {
                            storageManager.setOnboardingCompleted(
                                answer = null,
                                startingLevel = Level.GENTLE,
                                categoryIds = categoryIds
                            )
                            currentStep = OnboardingStep.HOW_IT_WORKS
                        }
                    )
                }
                OnboardingStep.HOW_IT_WORKS -> {
                    HowItWorksStep(
                        onComplete = {
                            val settings = storageManager.getSettings()
                            if (!settings.howItWorksSeen) {
                                storageManager.setOnboardingCompleted(
                                    answer = settings.onboardingAnswer,
                                    startingLevel = Level.GENTLE,
                                    categoryIds = categoryIds
                                )
                            }
                            onFinishOnboarding()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NameStep(
    nameInput: String,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.name_prompt_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onboarding_name_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(
                value = nameInput,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.name_field_label)) },
                placeholder = { Text(stringResource(R.string.name_field_hint)) },
                singleLine = true,
                maxLines = 1,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { onNext() }
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AffirmationOutlinedButton(
                text = stringResource(R.string.skip_button),
                onClick = onSkip,
                modifier = Modifier.weight(1f)
            )
            AffirmationButton(
                text = stringResource(R.string.next_button),
                onClick = onNext,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuestionStep(
    onOptionSelected: (optionText: String, level: Level) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.onboarding_question),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Option 1: "I believe it" -> MEDIUM
            val optBelieve = stringResource(R.string.onboarding_opt_believe)
            AffirmationCard(
                onClick = { onOptionSelected(optBelieve, Level.MEDIUM) },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
            ) {
                Text(
                    text = optBelieve,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Option 2: "I want to believe it" -> GENTLE
            val optWant = stringResource(R.string.onboarding_opt_want)
            AffirmationCard(
                onClick = { onOptionSelected(optWant, Level.GENTLE) },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
            ) {
                Text(
                    text = optWant,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Option 3: "I doubt it" -> GENTLE
            val optDoubt = stringResource(R.string.onboarding_opt_doubt)
            AffirmationCard(
                onClick = { onOptionSelected(optDoubt, Level.GENTLE) },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
            ) {
                Text(
                    text = optDoubt,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        AffirmationOutlinedButton(
            text = stringResource(R.string.skip_button),
            onClick = onSkip,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun HowItWorksStep(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.how_it_works_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Card 1: Read it slowly, once or twice. Say it out loud if you can.
            AffirmationCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.how_card_1),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 2: Ask yourself if it feels true. "Not yet" is a fine answer.
            AffirmationCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.how_card_2),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 3: Do the small step under the sentence. That is the whole practice. About 2 minutes.
            AffirmationCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.how_card_3),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        AffirmationButton(
            text = stringResource(R.string.start_button),
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )
    }
}
