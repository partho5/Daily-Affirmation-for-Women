package com.jovoc.dailyaffirmationwomen.ui.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jovoc.dailyaffirmationwomen.R
import com.jovoc.dailyaffirmationwomen.data.model.DatasetState
import com.jovoc.dailyaffirmationwomen.data.model.Level
import com.jovoc.dailyaffirmationwomen.data.model.SessionMode
import com.jovoc.dailyaffirmationwomen.data.model.UserSentence
import com.jovoc.dailyaffirmationwomen.data.repository.DatasetRepository
import com.jovoc.dailyaffirmationwomen.data.storage.LocalStorageManager
import com.jovoc.dailyaffirmationwomen.domain.FormattedSentence
import com.jovoc.dailyaffirmationwomen.domain.SentenceSelector
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationButton
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationCard
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationOutlinedButton
import com.jovoc.dailyaffirmationwomen.ui.components.BackButton
import com.jovoc.dailyaffirmationwomen.ui.components.DatasetErrorCard
import com.jovoc.dailyaffirmationwomen.ui.components.FavoriteIconButton
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Category Screen displaying category header, collapsible research note, built-in affirmations,
 * and user's own sentences.
 *
 * Source of truth: AGENT_INSTRUCTIONS.md Section 3.3, Section 4 (Features F3, F5) & Section 5:
 * - Top bar with BackButton (>= 48x48 dp) and category title.
 * - Header with category title, when_to_use, and "Start practice" button (launches session with next sentence).
 * - For category "my_values": secondary button "Take 10 minutes (optional)" navigating to DeepValues screen.
 * - Collapsible research note block displayed only if researchNoteId != null.
 * - Built-in sentences list:
 *   - Rendered at category's current level (gentle/medium/strong NEVER shown in UI).
 *   - Sentence text in 20-24sp font; prompt in 14-16sp.
 *   - Heart icon toggling favorite.
 *   - Tapping card starts session on that sentence.
 *   - {name} rule: if user name set, substituted; if name not set, shows prompt card linking to Settings.
 * - "Add my own sentence" button opening input dialog (enforcing 200 char max limit).
 * - User's own sentences list:
 *   - "Mine" badge.
 *   - Sentence text in 20-24sp font.
 *   - Heart icon for favorite.
 *   - Edit button (opens dialog) & Delete button (with confirmation dialog).
 *   - Tapping "Mine" card launches own sentence session on that sentence ID.
 */
@Composable
fun CategoryScreen(
    categoryId: String,
    storageManager: LocalStorageManager,
    repository: DatasetRepository,
    onBack: () -> Unit,
    onNavigateToSession: (String, String?) -> Unit,
    onNavigateToDeepValues: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSleepTimer: ((String) -> Unit)? = null
) {
    val datasetState by repository.state.collectAsState()
    val sessionLog by storageManager.sessionLogFlow.collectAsState()
    val categoryLevels by storageManager.categoryLevelsFlow.collectAsState()
    val favorites by storageManager.favoritesFlow.collectAsState()
    val userSentences by storageManager.userSentencesFlow.collectAsState()
    val settings by storageManager.settingsFlow.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var sentenceToEdit by remember { mutableStateOf<UserSentence?>(null) }
    var sentenceToDelete by remember { mutableStateOf<UserSentence?>(null) }

    when (val state = datasetState) {
        is DatasetState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
            }
        }

        is DatasetState.Error -> {
            Box(
                modifier = modifier
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

        is DatasetState.Success -> {
            val dataset = state.dataset
            val category = dataset.findCategoryById(categoryId)

            if (category == null) {
                // Defensive fallback if category id is not found
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    BackButton(onBack = onBack)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.dataset_error_desc),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return
            }

            val currentLevel = categoryLevels[category.id] ?: Level.GENTLE
            val todayDateStr = remember { LocalDate.now().toString() }
            val nextSentence = remember(category, sessionLog, todayDateStr) {
                if (category.affirmations.isNotEmpty()) {
                    SentenceSelector.getNextSentence(
                        category = category,
                        sessionLog = sessionLog,
                        todayDateStr = todayDateStr
                    )
                } else {
                    null
                }
            }

            val categoryUserSentences = remember(userSentences, category.id) {
                userSentences.filter { it.categoryId == category.id }
            }

            Column(
                modifier = modifier.fillMaxSize()
            ) {
                // Top Bar with BackButton and Category Title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BackButton(onBack = onBack)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Item: whenToUse, Start practice, Deep values button, Research note
                    item(key = "category_header_info") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (!category.audience.isNullOrBlank()) {
                                Text(
                                    text = category.audience,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = category.whenToUse,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // "Start practice" button
                            AffirmationButton(
                                text = stringResource(R.string.start_practice),
                                enabled = nextSentence != null,
                                onClick = {
                                    if (category.sessionMode == SessionMode.PAPER_TIMER && onNavigateToSleepTimer != null) {
                                        onNavigateToSleepTimer(category.id)
                                    } else {
                                        nextSentence?.let { onNavigateToSession(category.id, it.id) }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Secondary button for "my_values"
                            if (category.id == "my_values") {
                                Spacer(modifier = Modifier.height(10.dp))
                                AffirmationOutlinedButton(
                                    text = stringResource(R.string.deep_values_button),
                                    onClick = { onNavigateToDeepValues(category.id) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Collapsible Research Note Block
                            if (category.researchNoteId != null) {
                                val researchNote = dataset.researchNotes[category.researchNoteId]
                                if (researchNote != null) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    ResearchNoteBlock(researchNote = researchNote)
                                }
                            }
                        }
                    }

                    // Built-in Sentences List
                    items(
                        items = category.affirmations,
                        key = { it.id }
                    ) { affirmation ->
                        val rawText = affirmation.textForLevel(currentLevel)
                        val formatted = remember(rawText, settings.name) {
                            SentenceSelector.formatSentence(rawText, settings.name)
                        }

                        when (formatted) {
                            is FormattedSentence.Ready -> {
                                AffirmationCard(
                                    onClick = {
                                        if (category.sessionMode == SessionMode.PAPER_TIMER && onNavigateToSleepTimer != null) {
                                            onNavigateToSleepTimer(category.id)
                                        } else {
                                            onNavigateToSession(category.id, affirmation.id)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            // Sentence text in 20-24sp font (headlineSmall = 22sp)
                                            Text(
                                                text = formatted.text,
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (affirmation.prompt.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                // Prompt in 14-16sp font (bodySmall = 15sp)
                                                Text(
                                                    text = affirmation.prompt,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        FavoriteIconButton(
                                            isFavorite = favorites.contains(affirmation.id),
                                            onToggleFavorite = { storageManager.toggleFavorite(affirmation.id) }
                                        )
                                    }
                                }
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
                                        onClick = onNavigateToSettings
                                    )
                                }
                            }
                        }
                    }

                    // "Add my own sentence" Button
                    item(key = "add_custom_sentence_button") {
                        AffirmationOutlinedButton(
                            text = stringResource(R.string.add_my_own_sentence),
                            onClick = { showAddDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // User's Own Sentences List
                    items(
                        items = categoryUserSentences,
                        key = { it.id }
                    ) { userSentence ->
                        AffirmationCard(
                            onClick = { onNavigateToSession(category.id, userSentence.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // "Mine" Badge
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ) {
                                    Text(
                                        text = stringResource(R.string.mine_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                // Actions: Edit, Delete, Favorite
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(
                                        onClick = { sentenceToEdit = userSentence },
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                            .size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = stringResource(R.string.edit_button),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = { sentenceToDelete = userSentence },
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                            .size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = stringResource(R.string.delete_button),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    FavoriteIconButton(
                                        isFavorite = favorites.contains(userSentence.id),
                                        onToggleFavorite = { storageManager.toggleFavorite(userSentence.id) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // User sentence text in 20-24sp font (headlineSmall = 22sp)
                            Text(
                                text = userSentence.text,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Dialog for adding a custom sentence
            if (showAddDialog) {
                AddCustomSentenceDialog(
                    categoryId = category.id,
                    storageManager = storageManager,
                    sentenceToEdit = null,
                    onDismiss = { showAddDialog = false }
                )
            }

            // Dialog for editing an existing custom sentence
            if (sentenceToEdit != null) {
                AddCustomSentenceDialog(
                    categoryId = category.id,
                    storageManager = storageManager,
                    sentenceToEdit = sentenceToEdit,
                    onDismiss = { sentenceToEdit = null }
                )
            }

            // Confirmation dialog for deleting a custom sentence
            if (sentenceToDelete != null) {
                val toDelete = sentenceToDelete!!
                AlertDialog(
                    onDismissRequest = { sentenceToDelete = null },
                    title = {
                        Text(
                            text = stringResource(R.string.delete_sentence_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.delete_sentence_confirm_msg),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    confirmButton = {
                        AffirmationButton(
                            text = stringResource(R.string.delete_button),
                            onClick = {
                                storageManager.deleteUserSentence(toDelete.id)
                                sentenceToDelete = null
                            }
                        )
                    },
                    dismissButton = {
                        AffirmationOutlinedButton(
                            text = stringResource(R.string.cancel_button),
                            onClick = { sentenceToDelete = null }
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}
