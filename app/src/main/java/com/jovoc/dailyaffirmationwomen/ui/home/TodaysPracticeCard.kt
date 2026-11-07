package com.jovoc.dailyaffirmationwomen.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jovoc.dailyaffirmationwomen.R
import com.jovoc.dailyaffirmationwomen.data.model.Category
import com.jovoc.dailyaffirmationwomen.data.model.MetaSession
import com.jovoc.dailyaffirmationwomen.data.model.SessionLogEntry
import com.jovoc.dailyaffirmationwomen.data.model.SessionMode
import com.jovoc.dailyaffirmationwomen.data.storage.LocalStorageManager
import com.jovoc.dailyaffirmationwomen.domain.SuggestionEngine
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationButton
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationCard
import java.time.LocalTime

/**
 * Today's practice card shown at the top of the Home screen (Feature F2).
 *
 * Source of truth: AGENT_INSTRUCTIONS.md Section 3.1 & Section 4 (F2):
 * - Suggests category via [SuggestionEngine.getSuggestedCategory].
 * - Checks if a session has already been completed today (local device date).
 * - If completed today:
 *   - Displays done_today copy ("That is enough for today. Come back tomorrow, or open any category.") in calm styling.
 *   - Strictly does NOT show the Start button.
 * - If not completed today:
 *   - Header: today_card_title ("Today's practice").
 *   - Category title and when_to_use.
 *   - Time estimate: time_sleep ("About 5 minutes") for SessionMode.PAPER_TIMER,
 *     else time_standard ("About 2 minutes").
 *   - Start button: start_button ("Start") with >= 48x48 dp touch target.
 *   - Tapping Start calls [onStartPractice] with category.id and category.sessionMode.
 */
@Composable
fun TodaysPracticeCard(
    categories: List<Category>,
    metaSession: MetaSession,
    sessionLog: List<SessionLogEntry>,
    openedCategories: Set<String>,
    onStartPractice: (categoryId: String, sessionMode: SessionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    if (categories.isEmpty()) return

    val todayDateStr = remember(sessionLog) { LocalStorageManager.getTodayLocalDateString() }
    val hasCompletedToday = remember(sessionLog, todayDateStr) {
        sessionLog.any { it.date == todayDateStr }
    }

    if (hasCompletedToday) {
        AffirmationCard(
            modifier = modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentPadding = PaddingValues(20.dp)
        ) {
            Text(
                text = stringResource(R.string.today_card_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.done_today),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        return
    }

    val currentHour = remember(sessionLog) { LocalTime.now().hour }
    val suggestedCategoryId = remember(currentHour, todayDateStr, sessionLog, openedCategories, categories, metaSession) {
        SuggestionEngine.getSuggestedCategory(
            currentHour = currentHour,
            todayDateStr = todayDateStr,
            sessionLog = sessionLog,
            openedCategories = openedCategories,
            categories = categories,
            metaSession = metaSession
        )
    }

    val suggestedCategory = remember(suggestedCategoryId, categories) {
        categories.firstOrNull { it.id == suggestedCategoryId } ?: categories.firstOrNull()
    } ?: return

    AffirmationCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentPadding = PaddingValues(20.dp)
    ) {
        Text(
            text = stringResource(R.string.today_card_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = suggestedCategory.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = suggestedCategory.whenToUse,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        val timeEstimate = if (suggestedCategory.sessionMode == SessionMode.PAPER_TIMER) {
            stringResource(R.string.time_sleep)
        } else {
            stringResource(R.string.time_standard)
        }
        Text(
            text = timeEstimate,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        AffirmationButton(
            text = stringResource(R.string.start_button),
            onClick = {
                onStartPractice(suggestedCategory.id, suggestedCategory.sessionMode)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
