package com.jovoc.dailyaffirmationwomen.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.jovoc.dailyaffirmationwomen.R
import com.jovoc.dailyaffirmationwomen.data.storage.LocalStorageManager
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationButton
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationCard
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationOutlinedButton

/**
 * Six Reading Rules metadata adhering to AGENT_INSTRUCTIONS.md Section 3.4 & Section 5.
 */
object SettingsReadingRules {
    val ruleResIds: List<Int> = listOf(
        R.string.how_card_1,
        R.string.how_card_2,
        R.string.rule_3,
        R.string.rule_4,
        R.string.rule_5,
        R.string.rule_6
    )
}

/**
 * Settings bottom tab screen implementing Feature F7.
 * Source of truth: AGENT_INSTRUCTIONS.md Section 3.4, Section 4 (F7), Section 5, Section 7.
 *
 * Requirements:
 * - Collects `settings` from `storageManager.settingsFlow`.
 * - Name Editor:
 *   - Allows setting/updating first name.
 *   - Saves via `storageManager.setName(name)`.
 *   - Touch targets >= 48x48 dp.
 * - Six Reading Rules (from Section 3.4 & Section 5):
 *   - Title: `reading_rules_title` ("Reading rules").
 *   - 1: `how_card_1` ("Read it slowly, once or twice. Say it out loud if you can.")
 *   - 2: `how_card_2` ("Ask yourself if it feels true. \"Not yet\" is a fine answer.")
 *   - 3: `rule_3` ("If it does not feel true, think of a time it was true and a time it was not.")
 *   - 4: `rule_4` ("Do the small step under the sentence. It matters more than the sentence.")
 *   - 5: `rule_5` ("Stop after one to three sentences. One session a day is enough.")
 *   - 6: `rule_6` ("If something makes you feel worse, stop. Try another category or take a break.")
 * - Link to About screen:
 *   - Calm row or button navigating to `Screen.About`.
 * - Delete all my data:
 *   - Button `delete_all_data` ("Delete all my data").
 *   - Confirmation dialog with title `delete_data_confirm_title` and message `delete_data_confirm_msg`.
 *   - Upon confirm: calls `storageManager.deleteAllData()`, which wipes all SharedPreferences keys and resets state.
 *     Navigates to `Screen.Onboarding`.
 */
@Composable
fun SettingsScreen(
    storageManager: LocalStorageManager,
    onNavigateToAbout: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by storageManager.settingsFlow.collectAsState()
    var nameInput by remember(settings.name) { mutableStateOf(settings.name.orEmpty()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Top Heading
        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        // 1. Name Editor Card
        AffirmationCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.name_field_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.onboarding_name_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = nameInput,
                onValueChange = { if (it.length <= 50) nameInput = it },
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
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        storageManager.setName(nameInput)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            AffirmationButton(
                text = stringResource(R.string.save_button),
                onClick = {
                    focusManager.clearFocus()
                    storageManager.setName(nameInput)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 2. Six Reading Rules Card
        AffirmationCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.reading_rules_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingsReadingRules.ruleResIds.forEachIndexed { index, ruleResId ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(ruleResId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (index < SettingsReadingRules.ruleResIds.lastIndex) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        // 3. Link to About screen
        AffirmationCard(
            onClick = onNavigateToAbout,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.about_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.about_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 4. Delete All My Data Button
        AffirmationOutlinedButton(
            text = stringResource(R.string.delete_all_data),
            onClick = { showDeleteConfirmation = true },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Confirmation Dialog for Delete All Data
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(
                    text = stringResource(R.string.delete_data_confirm_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.delete_data_confirm_msg),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                AffirmationButton(
                    text = stringResource(R.string.delete_button),
                    onClick = {
                        showDeleteConfirmation = false
                        storageManager.deleteAllData()
                        onNavigateToOnboarding()
                    }
                )
            },
            dismissButton = {
                AffirmationOutlinedButton(
                    text = stringResource(R.string.cancel_button),
                    onClick = { showDeleteConfirmation = false }
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
