package com.jovoc.dailyaffirmationwomen.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jovoc.dailyaffirmationwomen.R
import com.jovoc.dailyaffirmationwomen.data.repository.DatasetRepository
import com.jovoc.dailyaffirmationwomen.data.storage.LocalStorageManager
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationButton
import com.jovoc.dailyaffirmationwomen.ui.components.CloseButton

/**
 * Deep Values Practice Screen (category `my_values` only).
 *
 * Source of truth: AGENT_INSTRUCTIONS.md Section 3.3 & Section 5:
 * - Plain screen for `my_values` deep practice.
 * - Displays `deep_values_text` ("Pick one value. Write about it for ten minutes: why it matters to you, and one time it guided you.").
 * - Ephemeral answer box with `answer_box_hint` ("Write a line (optional). It will not be saved.").
 *   CRITICAL RULE: Text is held strictly in ephemeral in-memory state; discarded on exit; never saved or logged.
 * - STRICT PROHIBITION: NO countdown timer!
 * - Displays end guidance message: `deep_values_end` ("You do not need to repeat this often.").
 * - Accessible small close button at top and "Done" button (`done_button`) at bottom to finish and exit.
 * - Records session in `sessionLog` on completion/exit without user text.
 */
@Composable
fun DeepValuesScreen(
    categoryId: String,
    storageManager: LocalStorageManager,
    repository: DatasetRepository,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val todayDateStr = remember { LocalStorageManager.getTodayLocalDateString() }

    // Ephemeral answer box text: strictly kept in-memory, never persisted or logged
    var ephemeralText by remember { mutableStateOf("") }
    var sessionRecorded by remember { mutableStateOf(false) }

    fun finishAndClose() {
        ephemeralText = "" // Strictly discard in-memory user text
        if (!sessionRecorded) {
            storageManager.recordSession(
                categoryId = categoryId,
                sentenceIds = emptyList(),
                localDate = todayDateStr
            )
            sessionRecorded = true
        }
        onClose()
    }

    // Intercept hardware and gesture back
    BackHandler {
        finishAndClose()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar with accessible CloseButton (>= 48x48 dp touch target)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CloseButton(onClose = { finishAndClose() })
            }

            // Main Content Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Instruction copy (Section 5 exact text)
                Text(
                    text = stringResource(R.string.deep_values_text),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )

                // Ephemeral answer box: strictly in-memory
                OutlinedTextField(
                    value = ephemeralText,
                    onValueChange = { ephemeralText = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.answer_box_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 280.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge
                )

                // End guidance message (Section 5 exact text)
                Text(
                    text = stringResource(R.string.deep_values_end),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Bottom action: "Done" button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                AffirmationButton(
                    text = stringResource(R.string.done_button),
                    onClick = { finishAndClose() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
