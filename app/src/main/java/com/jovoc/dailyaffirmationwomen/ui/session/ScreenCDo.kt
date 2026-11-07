package com.jovoc.dailyaffirmationwomen.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jovoc.dailyaffirmationwomen.R
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationButton

/**
 * Screen C: Do (~60 to 90 seconds).
 *
 * Implements AGENT_INSTRUCTIONS.md Section 3.2 & Section 5:
 * - Header: `do_header` ("One small step") and affirmation `prompt`.
 * - Hint: `do_time_hint` ("Take about a minute."). NO countdown timer.
 * - Ephemeral answer box with `answer_box_hint` ("Write a line (optional). It will not be saved.").
 *   CRITICAL RULE: Kept strictly in ephemeral in-memory state. Never persisted or logged.
 * - Button/Text link: `think_instead` ("I will think about it").
 * - Button: `next_button` ("Next") to proceed to Screen D.
 */
@Composable
fun ScreenCDo(
    prompt: String,
    ephemeralText: String,
    onEphemeralTextChange: (String) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onThinkInstead: () -> Unit = onNext
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top section: Header, prompt, and timing hint (NO countdown)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = stringResource(R.string.do_header),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.do_time_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Ephemeral answer box: strictly in-memory
            OutlinedTextField(
                value = ephemeralText,
                onValueChange = onEphemeralTextChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.answer_box_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 200.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                textStyle = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(12.dp))

            // "I will think about it" option for users who do not want to write
            TextButton(
                onClick = onThinkInstead,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Text(
                    text = stringResource(R.string.think_instead),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Bottom action button: Next
        AffirmationButton(
            text = stringResource(R.string.next_button),
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
