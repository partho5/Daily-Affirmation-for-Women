package com.jovoc.dailyaffirmationwomen.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jovoc.dailyaffirmationwomen.data.model.Category
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationCard

/**
 * Category item row in the Home screen list.
 *
 * Source of truth: AGENT_INSTRUCTIONS.md Section 4 (F2) & Section 6:
 * - Displays category title and when_to_use.
 * - Displays audience as a small subtitle if present.
 * - STRICT PROHIBITION: Never display developer-only fields:
 *   evidence, mechanism, best_time, session_mode, or suggest_policy.
 * - Minimum touch target >= 48x48 dp via AffirmationCard onClick.
 * - Invokes onCategoryClick with category.id when tapped.
 */
@Composable
fun CategoryRow(
    category: Category,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AffirmationCard(
        onClick = { onCategoryClick(category.id) },
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Text(
            text = category.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (!category.audience.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = category.audience,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = category.whenToUse,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
