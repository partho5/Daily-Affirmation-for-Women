package com.jovoc.dailyaffirmationwomen.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jovoc.dailyaffirmationwomen.R
import com.jovoc.dailyaffirmationwomen.data.model.Affirmation
import com.jovoc.dailyaffirmationwomen.data.model.Category
import com.jovoc.dailyaffirmationwomen.data.model.DatasetState
import com.jovoc.dailyaffirmationwomen.data.model.SessionMode
import com.jovoc.dailyaffirmationwomen.data.model.UserSentence
import com.jovoc.dailyaffirmationwomen.data.repository.DatasetRepository
import com.jovoc.dailyaffirmationwomen.data.storage.LocalStorageManager
import com.jovoc.dailyaffirmationwomen.domain.FormattedSentence
import com.jovoc.dailyaffirmationwomen.domain.SentenceSelector
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationCard
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationOutlinedButton
import com.jovoc.dailyaffirmationwomen.ui.components.DatasetErrorCard
import com.jovoc.dailyaffirmationwomen.ui.components.FavoriteIconButton
import kotlinx.coroutines.launch

/**
 * Navigation destination triggered from a favorited card.
 */
sealed class FavoritesNavigationTarget {
    data class Session(val categoryId: String, val sentenceId: String) : FavoritesNavigationTarget()
    data class SleepTimer(val categoryId: String) : FavoritesNavigationTarget()
}

/**
 * Pure helper logic for the Favorites screen to facilitate deterministic testing and clean routing.
 *
 * Source of truth: AGENT_INSTRUCTIONS.md Section 4 (Feature F6) & tasks.md Part 12:
 * - Built-in affirmations rendered at the category's current level.
 * - User sentences rendered with "Mine" badge (no levels apply per F5).
 * - Tapping card opens practice session (or SleepTimer for PAPER_TIMER mode).
 */
object FavoritesHelper {

    /**
     * Resolves the session navigation destination for a built-in affirmation.
     * PAPER_TIMER categories route to sleep timer; standard / say_aloud route to guided session.
     */
    fun resolveBuiltInNavigation(
        category: Category,
        affirmationId: String
    ): FavoritesNavigationTarget {
        return if (category.sessionMode == SessionMode.PAPER_TIMER) {
            FavoritesNavigationTarget.SleepTimer(category.id)
        } else {
            FavoritesNavigationTarget.Session(category.id, affirmationId)
        }
    }

    /**
     * Resolves the session navigation destination for a user-created affirmation.
     */
    fun resolveUserSentenceNavigation(
        userSentence: UserSentence
    ): FavoritesNavigationTarget.Session {
        return FavoritesNavigationTarget.Session(userSentence.categoryId, userSentence.id)
    }

    /**
     * Extracts built-in affirmations present in the favorites set, preserving category and affirmation order.
     */
    fun resolveFavoritedBuiltIn(
        categories: List<Category>,
        favorites: Set<String>
    ): List<Pair<Category, Affirmation>> {
        return categories.flatMap { category ->
            category.affirmations
                .filter { affirmation -> favorites.contains(affirmation.id) }
                .map { affirmation -> category to affirmation }
        }
    }

    /**
     * Extracts user sentences present in the favorites set.
     */
    fun resolveFavoritedUserSentences(
        userSentences: List<UserSentence>,
        favorites: Set<String>
    ): List<UserSentence> {
        return userSentences.filter { favorites.contains(it.id) }
    }
}

/**
 * Favorites Tab Screen (Feature F6).
 *
 * Source of truth: AGENT_INSTRUCTIONS.md Section 4 (F6), Section 7 & tasks.md Part 12:
 * - Collects favorites, dataset, userSentences, and categoryLevels reactively.
 * - Empty state: Displays exact stringResource(R.string.favorites_empty) ("No saved sentences yet.")
 *   in calm, uncluttered typography centered on screen.
 * - Content state:
 *   - LazyColumn displaying favorited built-in sentences and favorited user sentences.
 *   - Built-in sentences:
 *     - Rendered at the category's current level (storageManager.getCategoryLevel).
 *     - Formatted with SentenceSelector.formatSentence(rawText, userName). If name prompt needed, shows prompt card.
 *     - Displays sentence text (22sp), prompt (15sp), and category title badge/subtitle.
 *     - Heart icon (FavoriteIconButton) to toggle favorite.
 *     - Tapping card opens the sentence in practice session (or SleepTimer if PAPER_TIMER).
 *   - User's own sentences:
 *     - Marked with "Mine" badge (stringResource(R.string.mine_badge)).
 *     - Displays custom sentence text (22sp) and category title badge/subtitle.
 *     - Heart icon to toggle favorite.
 *     - Tapping card opens the own sentence in practice session.
 * - Touch targets >= 48x48 dp on cards, heart buttons, and action elements.
 * - Zero forbidden words; never displays internal level names ("gentle", "medium", "strong").
 */
@Composable
fun FavoritesScreen(
    storageManager: LocalStorageManager,
    repository: DatasetRepository,
    onNavigateToSession: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSleepTimer: ((String) -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null
) {
    val datasetState by repository.state.collectAsState()
    val favorites by storageManager.favoritesFlow.collectAsState()
    val userSentences by storageManager.userSentencesFlow.collectAsState()
    val categoryLevels by storageManager.categoryLevelsFlow.collectAsState()
    val settings by storageManager.settingsFlow.collectAsState()
    val coroutineScope = rememberCoroutineScope()

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

            val favoritedBuiltIn = remember(dataset, favorites) {
                FavoritesHelper.resolveFavoritedBuiltIn(dataset.categories, favorites)
            }
            val favoritedUser = remember(userSentences, favorites) {
                FavoritesHelper.resolveFavoritedUserSentences(userSentences, favorites)
            }
            val isEmpty = favoritedBuiltIn.isEmpty() && favoritedUser.isEmpty()

            if (isEmpty) {
                Column(
                    modifier = modifier.fillMaxSize()
                ) {
                    Text(
                        text = stringResource(R.string.nav_favorites),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.favorites_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "favorites_header_title") {
                        Text(
                            text = stringResource(R.string.nav_favorites),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Built-in favorited sentences
                    items(
                        items = favoritedBuiltIn,
                        key = { (_, affirmation) -> affirmation.id }
                    ) { (category, affirmation) ->
                        val currentLevel = categoryLevels[category.id] ?: storageManager.getCategoryLevel(category.id)
                        val rawText = affirmation.textForLevel(currentLevel)
                        val formatted = remember(rawText, settings.name) {
                            SentenceSelector.formatSentence(rawText, settings.name)
                        }

                        BuiltInFavoriteCard(
                            category = category,
                            affirmation = affirmation,
                            formattedSentence = formatted,
                            isFavorite = favorites.contains(affirmation.id),
                            onToggleFavorite = { storageManager.toggleFavorite(affirmation.id) },
                            onClick = {
                                when (val target = FavoritesHelper.resolveBuiltInNavigation(category, affirmation.id)) {
                                    is FavoritesNavigationTarget.SleepTimer -> {
                                        if (onNavigateToSleepTimer != null) {
                                            onNavigateToSleepTimer(target.categoryId)
                                        } else {
                                            onNavigateToSession(target.categoryId, affirmation.id)
                                        }
                                    }
                                    is FavoritesNavigationTarget.Session -> {
                                        onNavigateToSession(target.categoryId, target.sentenceId)
                                    }
                                }
                            },
                            onNavigateToSettings = onNavigateToSettings
                        )
                    }

                    // User's own favorited sentences
                    items(
                        items = favoritedUser,
                        key = { it.id }
                    ) { userSentence ->
                        val category = dataset.findCategoryById(userSentence.categoryId)
                        val categoryTitle = category?.title.orEmpty()

                        UserSentenceFavoriteCard(
                            userSentence = userSentence,
                            categoryTitle = categoryTitle,
                            isFavorite = favorites.contains(userSentence.id),
                            onToggleFavorite = { storageManager.toggleFavorite(userSentence.id) },
                            onClick = {
                                val target = FavoritesHelper.resolveUserSentenceNavigation(userSentence)
                                onNavigateToSession(target.categoryId, target.sentenceId)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Favorited card for built-in affirmation.
 * Supports formatted sentence with {name} substitution or prompt card if name is missing.
 */
@Composable
fun BuiltInFavoriteCard(
    category: Category,
    affirmation: Affirmation,
    formattedSentence: FormattedSentence,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    onNavigateToSettings: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    when (formattedSentence) {
        is FormattedSentence.Ready -> {
            AffirmationCard(
                onClick = onClick,
                modifier = modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    FavoriteIconButton(
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                // Sentence text in 20-24sp font (headlineSmall = 22sp)
                Text(
                    text = formattedSentence.text,
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
        }

        is FormattedSentence.NeedsNamePrompt -> {
            AffirmationCard(
                modifier = modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    FavoriteIconButton(
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
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
                if (onNavigateToSettings != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AffirmationOutlinedButton(
                        text = stringResource(R.string.go_to_settings),
                        onClick = onNavigateToSettings
                    )
                }
            }
        }
    }
}

/**
 * Favorited card for user-created affirmation.
 * Marked with "Mine" badge.
 */
@Composable
fun UserSentenceFavoriteCard(
    userSentence: UserSentence,
    categoryTitle: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AffirmationCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (categoryTitle.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(
                            text = categoryTitle,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                // "Mine" badge
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
            }
            FavoriteIconButton(
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        // Sentence text in 20-24sp font (headlineSmall = 22sp)
        Text(
            text = userSentence.text,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
