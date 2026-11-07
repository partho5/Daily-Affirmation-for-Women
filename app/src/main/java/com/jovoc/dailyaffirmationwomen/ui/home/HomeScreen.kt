package com.jovoc.dailyaffirmationwomen.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jovoc.dailyaffirmationwomen.data.model.DatasetState
import com.jovoc.dailyaffirmationwomen.data.model.SessionMode
import com.jovoc.dailyaffirmationwomen.data.repository.DatasetRepository
import com.jovoc.dailyaffirmationwomen.data.storage.LocalStorageManager
import com.jovoc.dailyaffirmationwomen.ui.components.DatasetErrorCard
import kotlinx.coroutines.launch

/**
 * Main Home screen displaying Today's Practice card and all categories in file order.
 *
 * Source of truth: AGENT_INSTRUCTIONS.md Section 3.1 & Section 4 (Feature F2):
 * - Collects dataset state and local storage state reactively.
 * - Shows a peaceful loading indicator or DatasetErrorCard if needed.
 * - Top: TodaysPracticeCard with suggested category and direct Start action.
 * - Below: All 12 categories from JSON in exact file order using CategoryRow.
 * - On category click: marks category opened via storageManager and navigates to CategoryDetail.
 * - On Start click: navigates to SleepTimer for PAPER_TIMER, else navigates to Session.
 */
@Composable
fun HomeScreen(
    storageManager: LocalStorageManager,
    repository: DatasetRepository,
    onNavigateToCategory: (String) -> Unit,
    onNavigateToSession: (String, String?) -> Unit,
    onNavigateToSleepTimer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val datasetState by repository.state.collectAsState()
    val sessionLog by storageManager.sessionLogFlow.collectAsState()
    val openedCategories by storageManager.openedCategoriesFlow.collectAsState()
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
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "todays_practice_card") {
                    TodaysPracticeCard(
                        categories = dataset.categories,
                        metaSession = dataset.metaSession,
                        sessionLog = sessionLog,
                        openedCategories = openedCategories,
                        onStartPractice = { categoryId, sessionMode ->
                            storageManager.markCategoryOpened(categoryId)
                            if (sessionMode == SessionMode.PAPER_TIMER) {
                                onNavigateToSleepTimer(categoryId)
                            } else {
                                onNavigateToSession(categoryId, null)
                            }
                        }
                    )
                }

                items(
                    items = dataset.categories,
                    key = { it.id }
                ) { category ->
                    CategoryRow(
                        category = category,
                        onCategoryClick = { categoryId ->
                            storageManager.markCategoryOpened(categoryId)
                            onNavigateToCategory(categoryId)
                        }
                    )
                }
            }
        }
    }
}
