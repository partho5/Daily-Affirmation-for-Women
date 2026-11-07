package com.jovoc.dailyaffirmationwomen.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jovoc.dailyaffirmationwomen.R
import com.jovoc.dailyaffirmationwomen.data.model.Category
import com.jovoc.dailyaffirmationwomen.data.model.SessionMode
import com.jovoc.dailyaffirmationwomen.domain.FormattedSentence
import com.jovoc.dailyaffirmationwomen.domain.SentenceSelector
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationButton
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationCard
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationOutlinedButton

/**
 * Screen A: Read (~20 seconds).
 *
 * Implements AGENT_INSTRUCTIONS.md Section 3.2 & Section 5:
 * - Instruction: `read_instruction_say_aloud` for SessionMode.SAY_ALOUD, else `read_instruction`.
 * - Sentence text rendered in 22sp (MaterialTheme.typography.headlineSmall).
 * - If user sentence, renders raw user text.
 * - If built-in sentence, formats using SentenceSelector.formatSentence(rawText, userName).
 * - Button: Next (`next_button`). Never forces a wait or delay.
 * - Zero repeat counters or loops (Research: repeating a positive statement made people with low self-esteem feel worse).
 * - If sentence requires name and name is missing, displays prompt card linking to Settings.
 */
@Composable
fun ScreenARead(
    sessionMode: SessionMode,
    sentenceText: String,
    isUserSentence: Boolean,
    userName: String?,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSettings: (() -> Unit)? = null
) {
    val instruction = if (sessionMode == SessionMode.SAY_ALOUD) {
        stringResource(R.string.read_instruction_say_aloud)
    } else {
        stringResource(R.string.read_instruction)
    }

    val formattedSentence = remember(sentenceText, userName, isUserSentence) {
        if (isUserSentence) {
            FormattedSentence.Ready(sentenceText)
        } else {
            SentenceSelector.formatSentence(sentenceText, userName)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Instruction text at top
        Text(
            text = instruction,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )

        // Sentence text in 20-24sp font (headlineSmall = 22sp) or Name prompt card
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            when (formattedSentence) {
                is FormattedSentence.Ready -> {
                    Text(
                        text = formattedSentence.text,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is FormattedSentence.NeedsNamePrompt -> {
                    AffirmationCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = stringResource(R.string.name_prompt_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.name_prompt_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        AffirmationOutlinedButton(
                            text = stringResource(R.string.go_to_settings),
                            onClick = { onNavigateToSettings?.invoke() }
                        )
                    }
                }
            }
        }

        // Bottom action button: Next (never forced delay, no repeat loops)
        if (formattedSentence is FormattedSentence.Ready) {
            AffirmationButton(
                text = stringResource(R.string.next_button),
                onClick = onNext,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ScreenARead(
    category: Category,
    sentenceText: String,
    isUserSentence: Boolean,
    userName: String?,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSettings: (() -> Unit)? = null
) = ScreenARead(
    sessionMode = category.sessionMode,
    sentenceText = sentenceText,
    isUserSentence = isUserSentence,
    userName = userName,
    onNext = onNext,
    modifier = modifier,
    onNavigateToSettings = onNavigateToSettings
)
