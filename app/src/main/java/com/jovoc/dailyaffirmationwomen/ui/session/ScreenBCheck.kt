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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jovoc.dailyaffirmationwomen.R
import com.jovoc.dailyaffirmationwomen.data.storage.LocalStorageManager
import com.jovoc.dailyaffirmationwomen.domain.LevelManager
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationButton
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationOutlinedButton

/**
 * Screen B: Check (~5 seconds).
 *
 * Implements AGENT_INSTRUCTIONS.md Section 3.2 & Section 5:
 * - Question: `check_question` ("Does it feel true right now?").
 * - Two buttons: `check_true` ("Feels true") and `check_not_yet` ("Not yet"). Accessible labels.
 * - On "Feels true": calls LevelManager.onFeelsTrue(currentLevel), updates level in storageManager,
 *   resets streak to 0, immediately proceeds to Screen C.
 * - On "Not yet": calls LevelManager.onNotYet(currentLevel, currentStreak), updates level in storageManager,
 *   increments streak. Displays `not_yet_message` ("That is okay. Think of one time it was true for you,
 *   and one time it was not."). If `showCategoryNoFit == true` (3 in a row at gentle), also displays
 *   `category_no_fit` ("This category may not fit you right now. You can try another one, or skip it for a while.").
 *   Displays "Next" button to proceed to Screen C.
 *
 * CRITICAL RULE:
 * Never show levels "gentle", "medium", "strong" to the user.
 */
@Composable
fun ScreenBCheck(
    categoryId: String,
    storageManager: LocalStorageManager,
    onProceedToScreenC: () -> Unit,
    modifier: Modifier = Modifier,
    noFitThreshold: Int = LevelManager.DEFAULT_NO_FIT_THRESHOLD
) {
    var hasAnsweredNotYet by remember { mutableStateOf(false) }
    var showCategoryNoFit by remember { mutableStateOf(false) }

    val feelsTrueDesc = stringResource(R.string.content_desc_feels_true)
    val notYetDesc = stringResource(R.string.content_desc_not_yet)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Question at top
        Text(
            text = stringResource(R.string.check_question),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )

        // Center: Guidance messages after "Not yet"
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (hasAnsweredNotYet) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.not_yet_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (showCategoryNoFit) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.category_no_fit),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Bottom actions
        if (!hasAnsweredNotYet) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // "Feels true" button: immediately proceeds to Screen C
                AffirmationButton(
                    text = stringResource(R.string.check_true),
                    onClick = {
                        val currentLevel = storageManager.getCategoryLevel(categoryId)
                        val result = LevelManager.onFeelsTrue(currentLevel)
                        storageManager.setCategoryLevel(categoryId, result.newLevel)
                        storageManager.resetNotYetStreak(categoryId)
                        onProceedToScreenC()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = feelsTrueDesc }
                )

                // "Not yet" button: adapts level, shows reflection message, then Next
                AffirmationOutlinedButton(
                    text = stringResource(R.string.check_not_yet),
                    onClick = {
                        val currentLevel = storageManager.getCategoryLevel(categoryId)
                        val currentStreak = storageManager.getNotYetStreak(categoryId)
                        val result = LevelManager.onNotYet(currentLevel, currentStreak, noFitThreshold)
                        storageManager.setCategoryLevel(categoryId, result.newLevel)
                        storageManager.incrementNotYetStreak(categoryId)
                        showCategoryNoFit = result.showCategoryNoFit
                        hasAnsweredNotYet = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = notYetDesc }
                )
            }
        } else {
            // Next button to proceed to Screen C after reading Not Yet reflection
            AffirmationButton(
                text = stringResource(R.string.next_button),
                onClick = onProceedToScreenC,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
