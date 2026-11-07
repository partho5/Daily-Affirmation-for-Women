package com.jovoc.dailyaffirmationwomen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.jovoc.dailyaffirmationwomen.data.repository.DatasetRepository
import com.jovoc.dailyaffirmationwomen.data.storage.LocalStorageManager
import com.jovoc.dailyaffirmationwomen.ui.navigation.AppNavHost
import com.jovoc.dailyaffirmationwomen.ui.navigation.Screen
import com.jovoc.dailyaffirmationwomen.ui.theme.DailyAffirmationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Main application entry point.
 *
 * Guarantees lightning-fast startup adhering to AGENT_INSTRUCTIONS.md Section 3.1:
 * - Immediate launch straight to Home or Onboarding with zero artificial delay or splash screen.
 * - Local state loaded instantly from SharedPreferences memory cache.
 * - Bundled JSON dataset parsed asynchronously on Dispatchers.IO.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Instant local state initialization (backed by in-memory SharedPreferences cache)
        val storageManager = LocalStorageManager.getInstance(applicationContext)

        // Asynchronously load and cache dataset in background without blocking UI thread
        val repository = DatasetRepository.getInstance(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) {
            repository.loadDatasetResult()
        }

        // Determine starting screen based on whether user has completed the onboarding flow
        val settings = storageManager.getSettings()
        val initialScreen = if (settings.howItWorksSeen) {
            Screen.Home
        } else {
            Screen.Onboarding
        }

        setContent {
            DailyAffirmationTheme {
                AppNavHost(
                    initialScreen = initialScreen,
                    storageManager = storageManager,
                    repository = repository
                )
            }
        }
    }
}
