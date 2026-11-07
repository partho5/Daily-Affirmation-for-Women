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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jovoc.dailyaffirmationwomen.R
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationButton
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationOutlinedButton
import com.jovoc.dailyaffirmationwomen.ui.components.FavoriteIconButton

/**
 * Screen D: Close (~10 seconds).
 *
 * Implements AGENT_INSTRUCTIONS.md Section 3.2 & Section 5:
 * - Message: `close_message` ("That is enough for now.").
 * - Favorite heart toggle button.
 * - `another_sentence` button: displayed ONLY if `sentencesCompletedCount < maxSentencesPerSession` (3).
 *   After that, only Done is available.
 * - `done_button` ("Done") button: finishes session.
 * - STRICT PROHIBITION: Zero streaks, zero reward metrics, zero badges, zero gamification animations.
 */
@Composable
fun ScreenDClose(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    sentencesCompletedCount: Int,
    maxSentencesPerSession: Int,
    onAnotherSentence: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    canShowAnotherSentence: Boolean = true
) {
    val showAnother = canShowAnotherSentence && (sentencesCompletedCount < maxSentencesPerSession)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Center content: Close message and Favorite heart
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.close_message),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                FavoriteIconButton(
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }

        // Bottom action buttons: "Another sentence" (if < max) and "Done"
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showAnother) {
                AffirmationOutlinedButton(
                    text = stringResource(R.string.another_sentence),
                    onClick = onAnotherSentence,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AffirmationButton(
                text = stringResource(R.string.done_button),
                onClick = onDone,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
