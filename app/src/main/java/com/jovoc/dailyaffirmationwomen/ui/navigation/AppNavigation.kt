package com.jovoc.dailyaffirmationwomen.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jovoc.dailyaffirmationwomen.R
import com.jovoc.dailyaffirmationwomen.data.repository.DatasetRepository
import com.jovoc.dailyaffirmationwomen.data.storage.LocalStorageManager
import com.jovoc.dailyaffirmationwomen.ui.category.CategoryScreen
import com.jovoc.dailyaffirmationwomen.ui.components.AffirmationButton
import com.jovoc.dailyaffirmationwomen.ui.components.BackButton
import com.jovoc.dailyaffirmationwomen.ui.components.CloseButton
import com.jovoc.dailyaffirmationwomen.ui.favorites.FavoritesScreen
import com.jovoc.dailyaffirmationwomen.ui.home.HomeScreen
import com.jovoc.dailyaffirmationwomen.ui.onboarding.OnboardingScreen
import com.jovoc.dailyaffirmationwomen.ui.session.DeepValuesScreen
import com.jovoc.dailyaffirmationwomen.ui.session.SessionScreen
import com.jovoc.dailyaffirmationwomen.ui.session.SleepTimerScreen
import com.jovoc.dailyaffirmationwomen.ui.settings.AboutScreen
import com.jovoc.dailyaffirmationwomen.ui.settings.SettingsScreen
import com.jovoc.dailyaffirmationwomen.ui.theme.SleepTheme

// ============================================================================
// Screen Definitions
// Strictly adheres to AGENT_INSTRUCTIONS.md Section 7 & tasks.md Part 6
// ============================================================================

sealed class Screen {
    object Home : Screen()
    object Favorites : Screen()
    object Settings : Screen()
    object Onboarding : Screen()
    data class CategoryDetail(val categoryId: String) : Screen()
    data class Session(val categoryId: String, val sentenceId: String? = null) : Screen()
    data class SleepTimer(val categoryId: String) : Screen()
    data class DeepValues(val categoryId: String) : Screen()
    object About : Screen()
}

/**
 * Determines whether the bottom navigation bar is visible.
 *
 * Visible ONLY on:
 * - Home
 * - Favorites
 * - Settings
 * - CategoryDetail
 * - About
 *
 * Hidden on full-screen flows:
 * - Session
 * - SleepTimer
 * - DeepValues
 * - Onboarding
 */
val Screen.isBottomBarVisible: Boolean
    get() = when (this) {
        is Screen.Home,
        is Screen.Favorites,
        is Screen.Settings,
        is Screen.CategoryDetail,
        is Screen.About -> true
        is Screen.Session,
        is Screen.SleepTimer,
        is Screen.DeepValues,
        is Screen.Onboarding -> false
    }

/**
 * Maps the current screen to its active tab on the bottom navigation bar, if any.
 */
val Screen.selectedTab: Screen?
    get() = when (this) {
        is Screen.Home, is Screen.CategoryDetail -> Screen.Home
        is Screen.Favorites -> Screen.Favorites
        is Screen.Settings, is Screen.About -> Screen.Settings
        else -> null
    }

// ============================================================================
// Lightweight Navigator
// Zero third-party library bloat. Built-in State-driven backstack management.
// ============================================================================

class Navigator(initialScreen: Screen = Screen.Home) {
    private val _backStack = mutableStateListOf<Screen>(initialScreen)
    val backStack: List<Screen> get() = _backStack

    val currentScreen: Screen
        get() = _backStack.lastOrNull() ?: Screen.Home

    val canGoBack: Boolean
        get() = _backStack.size > 1

    fun navigateTo(screen: Screen) {
        if (_backStack.lastOrNull() != screen) {
            _backStack.add(screen)
        }
    }

    fun replace(screen: Screen) {
        if (_backStack.isNotEmpty()) {
            _backStack.removeAt(_backStack.lastIndex)
        }
        _backStack.add(screen)
    }

    fun switchTab(screen: Screen) {
        if (currentScreen == screen) return

        if (screen is Screen.Home || screen is Screen.Favorites || screen is Screen.Settings) {
            val existingIndex = _backStack.indexOf(screen)
            if (existingIndex >= 0) {
                while (_backStack.size > existingIndex + 1) {
                    _backStack.removeAt(_backStack.lastIndex)
                }
            } else {
                _backStack.add(screen)
            }
        } else {
            navigateTo(screen)
        }
    }

    fun goBack(): Boolean {
        if (_backStack.size > 1) {
            _backStack.removeAt(_backStack.lastIndex)
            return true
        }
        return false
    }

    fun popToRoot() {
        if (_backStack.size > 1) {
            val root = _backStack.first()
            _backStack.clear()
            _backStack.add(root)
        }
    }

    fun resetTo(screen: Screen) {
        _backStack.clear()
        _backStack.add(screen)
    }
}

@Composable
fun rememberNavigator(initialScreen: Screen = Screen.Home): Navigator {
    return remember { Navigator(initialScreen) }
}

// ============================================================================
// Bottom Navigation Bar
// ============================================================================

@Composable
fun AppBottomNavigationBar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedTab = currentScreen.selectedTab

    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        // Tab 1: Home
        NavigationBarItem(
            selected = selectedTab == Screen.Home,
            onClick = { onNavigate(Screen.Home) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = stringResource(R.string.nav_home)
                )
            },
            label = { Text(stringResource(R.string.nav_home)) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        // Tab 2: Favorites
        NavigationBarItem(
            selected = selectedTab == Screen.Favorites,
            onClick = { onNavigate(Screen.Favorites) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = stringResource(R.string.nav_favorites)
                )
            },
            label = { Text(stringResource(R.string.nav_favorites)) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        // Tab 3: Settings
        NavigationBarItem(
            selected = selectedTab == Screen.Settings,
            onClick = { onNavigate(Screen.Settings) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.nav_settings)
                )
            },
            label = { Text(stringResource(R.string.nav_settings)) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

// ============================================================================
// App Navigation Host & Transition Container
// ============================================================================

@Composable
fun AppNavHost(
    initialScreen: Screen = Screen.Home,
    navigator: Navigator = rememberNavigator(initialScreen),
    storageManager: LocalStorageManager,
    repository: DatasetRepository,
    modifier: Modifier = Modifier
) {
    // Intercept hardware and gesture back actions
    BackHandler(enabled = navigator.canGoBack) {
        navigator.goBack()
    }

    val currentScreen = navigator.currentScreen
    val showBottomBar = currentScreen.isBottomBarVisible

    // Detect system reduced motion setting (< 250ms fade requirement)
    val context = LocalContext.current
    val isReducedMotion = remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }
    val animationSpec = remember(isReducedMotion) {
        tween<Float>(durationMillis = if (isReducedMotion) 0 else 200)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                AppBottomNavigationBar(
                    currentScreen = currentScreen,
                    onNavigate = { targetScreen ->
                        navigator.switchTab(targetScreen)
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(
                targetState = currentScreen,
                animationSpec = animationSpec,
                label = "ScreenTransition"
            ) { targetScreen ->
                when (targetScreen) {
                    is Screen.Home -> {
                        HomeScreen(
                            storageManager = storageManager,
                            repository = repository,
                            onNavigateToCategory = { catId ->
                                navigator.navigateTo(Screen.CategoryDetail(catId))
                            },
                            onNavigateToSession = { catId, sentId ->
                                navigator.navigateTo(Screen.Session(catId, sentId))
                            },
                            onNavigateToSleepTimer = { catId ->
                                navigator.navigateTo(Screen.SleepTimer(catId))
                            }
                        )
                    }
                    is Screen.Favorites -> {
                        FavoritesScreen(
                            storageManager = storageManager,
                            repository = repository,
                            onNavigateToSession = { catId, sentId ->
                                navigator.navigateTo(Screen.Session(catId, sentId))
                            },
                            onNavigateToSleepTimer = { catId ->
                                navigator.navigateTo(Screen.SleepTimer(catId))
                            },
                            onNavigateToSettings = {
                                navigator.switchTab(Screen.Settings)
                            }
                        )
                    }
                    is Screen.Settings -> {
                        SettingsScreen(
                            storageManager = storageManager,
                            onNavigateToAbout = {
                                navigator.navigateTo(Screen.About)
                            },
                            onNavigateToOnboarding = {
                                navigator.resetTo(Screen.Onboarding)
                            }
                        )
                    }
                    is Screen.Onboarding -> {
                        OnboardingScreen(
                            storageManager = storageManager,
                            repository = repository,
                            onFinishOnboarding = {
                                navigator.replace(Screen.Home)
                            }
                        )
                    }
                    is Screen.CategoryDetail -> {
                        CategoryScreen(
                            categoryId = targetScreen.categoryId,
                            storageManager = storageManager,
                            repository = repository,
                            onBack = { navigator.goBack() },
                            onNavigateToSession = { catId, sentId ->
                                navigator.navigateTo(Screen.Session(catId, sentId))
                            },
                            onNavigateToSleepTimer = { catId ->
                                navigator.navigateTo(Screen.SleepTimer(catId))
                            },
                            onNavigateToDeepValues = { catId ->
                                navigator.navigateTo(Screen.DeepValues(catId))
                            },
                            onNavigateToSettings = {
                                navigator.switchTab(Screen.Settings)
                            }
                        )
                    }
                    is Screen.Session -> {
                        SessionScreen(
                            categoryId = targetScreen.categoryId,
                            sentenceId = targetScreen.sentenceId,
                            storageManager = storageManager,
                            repository = repository,
                            onClose = { navigator.goBack() },
                            onNavigateToSettings = {
                                navigator.switchTab(Screen.Settings)
                            }
                        )
                    }
                    is Screen.SleepTimer -> {
                        SleepTimerScreen(
                            categoryId = targetScreen.categoryId,
                            storageManager = storageManager,
                            repository = repository,
                            onClose = { navigator.goBack() },
                            onNavigateToSettings = {
                                navigator.switchTab(Screen.Settings)
                            }
                        )
                    }
                    is Screen.DeepValues -> {
                        DeepValuesScreen(
                            categoryId = targetScreen.categoryId,
                            storageManager = storageManager,
                            repository = repository,
                            onClose = { navigator.goBack() }
                        )
                    }
                    is Screen.About -> {
                        AboutScreen(
                            onBack = { navigator.goBack() }
                        )
                    }
                }
            }
        }
    }
}
