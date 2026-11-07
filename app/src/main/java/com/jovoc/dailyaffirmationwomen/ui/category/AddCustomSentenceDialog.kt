package com.jovoc.dailyaffirmationwomen.ui.category

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jovoc.dailyaffirmationwomen.R
import com.jovoc.dailyaffirmationwomen.data.model.UserSentence
import com.jovoc.dailyaffirmationwomen.data.storage.LocalStorageManager
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationButton
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationOutlinedButton

private const val MAX_SENTENCE_CHARS = 200

/**
 * Dialog for adding or editing a custom affirmation sentence.
 *
 * Source of truth: AGENT_INSTRUCTIONS.md Section 4 (Feature F5) & Section 5:
 * - Hint text: stringResource(R.string.user_added_sentence_hint) ("Write something you can believe today.").
 * - Strict 200-character limit enforced in both UI input handling and persistence.
 * - Live character counter (e.g. "45/200").
 * - Save and Cancel buttons with touch targets >= 48x48 dp.
 * - Persists locally via LocalStorageManager.saveUserSentence (prefixed with "u_")
 *   or LocalStorageManager.updateUserSentence.
 */
@Composable
fun AddCustomSentenceDialog(
    categoryId: String,
    storageManager: LocalStorageManager,
    onDismiss: () -> Unit,
    sentenceToEdit: UserSentence? = null,
    onSaved: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var text by remember(sentenceToEdit) {
        mutableStateOf(sentenceToEdit?.text ?: "")
    }
    val isEditing = sentenceToEdit != null
    val currentTrimmed = text.trim()
    val isSaveEnabled = currentTrimmed.isNotEmpty() && currentTrimmed.length <= MAX_SENTENCE_CHARS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) {
                    stringResource(R.string.edit_sentence_title)
                } else {
                    stringResource(R.string.add_my_own_sentence)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.user_added_sentence_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        // Enforce strict 200-character maximum limit
                        text = if (input.length <= MAX_SENTENCE_CHARS) input else input.take(MAX_SENTENCE_CHARS)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.user_added_sentence_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    supportingText = {
                        Text(
                            text = "${text.length}/$MAX_SENTENCE_CHARS",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            AffirmationButton(
                text = stringResource(R.string.save_button),
                enabled = isSaveEnabled,
                onClick = {
                    if (isSaveEnabled) {
                        if (isEditing && sentenceToEdit != null) {
                            storageManager.updateUserSentence(sentenceToEdit.id, currentTrimmed)
                        } else {
                            storageManager.saveUserSentence(categoryId, currentTrimmed)
                        }
                        onSaved()
                        onDismiss()
                    }
                }
            )
        },
        dismissButton = {
            AffirmationOutlinedButton(
                text = stringResource(R.string.cancel_button),
                onClick = onDismiss
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    )
}
